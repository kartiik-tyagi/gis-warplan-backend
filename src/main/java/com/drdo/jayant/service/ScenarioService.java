package com.drdo.jayant.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drdo.jayant.domain.AppUser;
import com.drdo.jayant.domain.FeatureCodes;
import com.drdo.jayant.domain.Game;
import com.drdo.jayant.domain.PlayingOrbat;
import com.drdo.jayant.domain.Scenario;
import com.drdo.jayant.repository.AppUserRepository;
import com.drdo.jayant.repository.GameRepository;
import com.drdo.jayant.repository.PlayingOrbatRepository;
import com.drdo.jayant.repository.ScenarioRepository;
import com.drdo.jayant.web.ConflictException;
import com.drdo.jayant.web.NotFoundException;

/**
 * Scenarios and games. At most one scenario and one game are active; the active game always belongs to the
 * active scenario.
 */
@Service
public class ScenarioService {

    public static final String GAME_DRAFT = "DRAFT";
    public static final String GAME_ACTIVE = "ACTIVE";
    public static final String GAME_INACTIVE = "INACTIVE";

    private final ScenarioRepository scenarios;
    private final GameRepository games;
    private final PlayingOrbatRepository playingOrbats;
    private final AppUserRepository users;
    private final ThemeService themes;
    private final AccessService access;
    private final AuditService audit;

    public ScenarioService(ScenarioRepository scenarios, GameRepository games, PlayingOrbatRepository playingOrbats,
            AppUserRepository users, ThemeService themes, AccessService access, AuditService audit) {
        this.scenarios = scenarios;
        this.games = games;
        this.playingOrbats = playingOrbats;
        this.users = users;
        this.themes = themes;
        this.access = access;
        this.audit = audit;
    }

    // ---- scenarios

    @Transactional(readOnly = true)
    public List<Scenario> listScenarios() {
        access.requireFeature(FeatureCodes.MANAGE_SCENARIO);
        return scenarios.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Scenario getScenario(Long id) {
        access.requireFeature(FeatureCodes.MANAGE_SCENARIO);
        return findScenario(id);
    }

    @Transactional
    public Scenario createScenario(String name, String description, Long themeId, Map<String, Object> configJson) {
        access.requireFeature(FeatureCodes.MANAGE_SCENARIO);
        themes.requireExists(themeId);
        Scenario scenario = new Scenario();
        scenario.setName(name.trim());
        scenario.setDescription(description);
        scenario.setThemeId(themeId);
        scenario.setConfigJson(configJson);
        Scenario saved = scenarios.save(scenario);
        audit.record("CREATE", "Scenario", saved.getId(), "Scenario " + saved.getName() + " created", Map.of());
        return saved;
    }

    @Transactional
    public Scenario updateScenario(Long id, String name, String description, Long themeId,
            Map<String, Object> configJson) {
        access.requireFeature(FeatureCodes.MANAGE_SCENARIO);
        Scenario scenario = findScenario(id);
        themes.requireExists(themeId);
        scenario.setName(name.trim());
        scenario.setDescription(description);
        scenario.setThemeId(themeId);
        scenario.setConfigJson(configJson);
        Scenario saved = scenarios.save(scenario);
        audit.record("UPDATE", "Scenario", saved.getId(), "Scenario " + saved.getName() + " updated", Map.of());
        return saved;
    }

    /** Removes a scenario with its games and playing ORBATs; users assigned to those ORBATs are unassigned. */
    @Transactional
    public void deleteScenario(Long id) {
        access.requireFeature(FeatureCodes.MANAGE_SCENARIO);
        Scenario scenario = findScenario(id);
        if (scenario.isActive()) {
            throw new ConflictException("The active scenario cannot be deleted.");
        }
        for (PlayingOrbat orbat : playingOrbats.findByScenarioId(id)) {
            for (AppUser assigned : users.findByPlayingOrbatId(orbat.getId())) {
                assigned.setPlayingOrbatId(null);
                assigned.setPlayingNodeId(null);
                users.save(assigned);
            }
            playingOrbats.delete(orbat);
        }
        games.deleteAll(games.findByScenarioId(id));
        scenarios.delete(scenario);
        audit.record("DELETE", "Scenario", id, "Scenario " + scenario.getName() + " deleted", Map.of());
    }

    /** Makes the scenario the only active one; games of other scenarios are deactivated. */
    @Transactional
    public Scenario activateScenario(Long id) {
        access.requireFeature(FeatureCodes.MANAGE_SCENARIO);
        Scenario scenario = findScenario(id);
        activate(scenario);
        audit.record("ACTIVATE", "Scenario", id, "Scenario " + scenario.getName() + " activated", Map.of());
        return scenario;
    }

    // ---- games

    @Transactional(readOnly = true)
    public List<Game> listGames(Long scenarioId) {
        access.requireFeature(FeatureCodes.MANAGE_GAME);
        findScenario(scenarioId);
        return games.findByScenarioId(scenarioId);
    }

    @Transactional(readOnly = true)
    public Game getGame(Long id) {
        access.requireFeature(FeatureCodes.MANAGE_GAME);
        return findGame(id);
    }

    @Transactional
    public Game createGame(Long scenarioId, String name, Map<String, Object> configJson) {
        access.requireFeature(FeatureCodes.MANAGE_GAME);
        findScenario(scenarioId);
        Game game = new Game();
        game.setName(name.trim());
        game.setScenarioId(scenarioId);
        game.setStatus(GAME_DRAFT);
        game.setConfigJson(configJson);
        Game saved = games.save(game);
        audit.record("CREATE", "Game", saved.getId(), "Game " + saved.getName() + " created",
                Map.of("scenarioId", scenarioId));
        return saved;
    }

    @Transactional
    public Game updateGame(Long id, String name, Map<String, Object> configJson) {
        access.requireFeature(FeatureCodes.MANAGE_GAME);
        Game game = findGame(id);
        game.setName(name.trim());
        game.setConfigJson(configJson);
        Game saved = games.save(game);
        audit.record("UPDATE", "Game", saved.getId(), "Game " + saved.getName() + " updated", Map.of());
        return saved;
    }

    @Transactional
    public void deleteGame(Long id) {
        access.requireFeature(FeatureCodes.MANAGE_GAME);
        Game game = findGame(id);
        if (game.isActive()) {
            throw new ConflictException("The active game cannot be deleted.");
        }
        games.delete(game);
        audit.record("DELETE", "Game", id, "Game " + game.getName() + " deleted", Map.of());
    }

    /** Makes the game the only active one and activates its scenario too. */
    @Transactional
    public Game activateGame(Long id) {
        access.requireFeature(FeatureCodes.MANAGE_GAME);
        Game game = findGame(id);
        Scenario scenario = findScenario(game.getScenarioId());
        activate(scenario);
        for (Game other : games.findByActiveTrue()) {
            if (!other.getId().equals(id)) {
                other.setActive(false);
                other.setStatus(GAME_INACTIVE);
                games.save(other);
            }
        }
        game.setActive(true);
        game.setStatus(GAME_ACTIVE);
        if (game.getStartedAt() == null) {
            game.setStartedAt(Instant.now());
        }
        Game saved = games.save(game);
        audit.record("ACTIVATE", "Game", id, "Game " + game.getName() + " activated",
                Map.of("scenarioId", scenario.getId()));
        return saved;
    }

    // ---- shared

    Scenario findScenario(Long id) {
        return scenarios.findById(id).orElseThrow(() -> new NotFoundException("Scenario not found."));
    }

    private Game findGame(Long id) {
        return games.findById(id).orElseThrow(() -> new NotFoundException("Game not found."));
    }

    private void activate(Scenario scenario) {
        if (scenario.isActive()) {
            return;
        }
        for (Scenario other : scenarios.findByActiveTrue()) {
            other.setActive(false);
            scenarios.save(other);
        }
        for (Game game : games.findByActiveTrue()) {
            if (!scenario.getId().equals(game.getScenarioId())) {
                game.setActive(false);
                game.setStatus(GAME_INACTIVE);
                games.save(game);
            }
        }
        scenario.setActive(true);
        scenarios.save(scenario);
    }
}
