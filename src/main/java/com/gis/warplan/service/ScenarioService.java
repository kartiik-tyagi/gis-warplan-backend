package com.gis.warplan.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.AllianceMember;
import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.Game;
import com.gis.warplan.domain.MoonPhase;
import com.gis.warplan.domain.PlayingOrbat;
import com.gis.warplan.domain.Scenario;
import com.gis.warplan.domain.Side;
import com.gis.warplan.repository.AllianceMemberRepository;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.GameRepository;
import com.gis.warplan.repository.PlayingOrbatRepository;
import com.gis.warplan.repository.ScenarioRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.NotFoundException;

/**
 * Scenarios and games. At most one scenario and one game are active; the active game always belongs to the
 * active scenario.
 */
@Service
public class ScenarioService {

    private static final Logger log = LogManager.getLogger(ScenarioService.class);

    private final ScenarioRepository scenarios;
    private final GameRepository games;
    private final PlayingOrbatRepository playingOrbats;
    private final AppUserRepository users;
    private final SideRepository sides;
    private final AllianceMemberRepository allianceMembers;
    private final AccessService access;
    private final AuditService audit;
    private final ScenarioAttachmentService attachments;
    private final ActivationService activation;

    public ScenarioService(ScenarioRepository scenarios, GameRepository games, PlayingOrbatRepository playingOrbats,
            AppUserRepository users, SideRepository sides, AllianceMemberRepository allianceMembers,
            AccessService access, AuditService audit, ScenarioAttachmentService attachments,
            ActivationService activation) {
        this.scenarios = scenarios;
        this.games = games;
        this.playingOrbats = playingOrbats;
        this.users = users;
        this.sides = sides;
        this.allianceMembers = allianceMembers;
        this.access = access;
        this.audit = audit;
        this.attachments = attachments;
        this.activation = activation;
    }

    // ---- scenarios

    @Transactional(readOnly = true)
    public List<Scenario> listScenarios() {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SCENARIO);
        return scenarios.findAll(Sort.by(Sort.Order.asc("name").ignoreCase()));
    }

    @Transactional(readOnly = true)
    public Scenario getScenario(Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SCENARIO);
        return findScenario(id);
    }

    @Transactional
    public Scenario createScenario(String name, String generalIdea, String openingNarrative, String specialIdea) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SCENARIO);
        Scenario scenario = new Scenario();
        applyScenarioFields(scenario, name, generalIdea, openingNarrative, specialIdea);
        access.currentPrincipal().ifPresent(principal -> scenario.setCreatedBy(principal.userId()));
        Scenario saved = scenarios.save(scenario);
        log.info("scenario.created id={} name={}", saved.getId(), saved.getName());
        audit.record("CREATE", "Scenario", saved.getId(), "Scenario " + saved.getName() + " created", Map.of());
        return saved;
    }

    @Transactional
    public Scenario updateScenario(Long id, String name, String generalIdea, String openingNarrative,
            String specialIdea) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SCENARIO);
        Scenario scenario = findScenario(id);
        applyScenarioFields(scenario, name, generalIdea, openingNarrative, specialIdea);
        Scenario saved = scenarios.save(scenario);
        log.info("scenario.updated id={}", saved.getId());
        audit.record("UPDATE", "Scenario", saved.getId(), "Scenario " + saved.getName() + " updated", Map.of());
        return saved;
    }

    /** Removes a scenario with its games and playing ORBATs; users assigned to those ORBATs are unassigned. */
    @Transactional
    public void deleteScenario(Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SCENARIO);
        Scenario scenario = findScenario(id);
        if (scenario.isActive()) {
            log.warn("scenario.delete_rejected id={} reason=active", id);
            throw new ConflictException("The active scenario cannot be deleted.");
        }
        for (Game game : games.findByScenarioId(id)) {
            purgeGame(game);
        }
        attachments.purge(id);
        scenarios.delete(scenario);
        activation.reconcile();
        log.info("scenario.deleted id={}", id);
        audit.record("DELETE", "Scenario", id, "Scenario " + scenario.getName() + " deleted", Map.of());
    }

    /** Makes the scenario the only active one; games of other scenarios are deactivated. */
    @Transactional
    public Scenario activateScenario(Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SCENARIO);
        Scenario scenario = findScenario(id);
        activate(scenario);
        activation.reconcile();
        log.info("scenario.activated id={}", id);
        audit.record("ACTIVATE", "Scenario", id, "Scenario " + scenario.getName() + " activated", Map.of());
        return scenario;
    }

    /**
     * Clears the open exercise: every scenario and every game becomes inactive.
     * Requires MANAGE_SCENARIO (same capability that chooses the active scenario).
     */
    @Transactional
    public void deactivateAll() {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SCENARIO);
        int scenariosCleared = 0;
        int gamesCleared = 0;
        for (Scenario scenario : scenarios.findByActiveTrue()) {
            scenario.setActive(false);
            scenarios.save(scenario);
            scenariosCleared++;
        }
        gamesCleared = clearActiveGames();
        activation.reconcile();
        log.info("exercise.deactivated scenarios={} games={}", scenariosCleared, gamesCleared);
        audit.record("DEACTIVATE", "Scenario", null, "No active scenario or game",
                Map.of("scenariosCleared", scenariosCleared, "gamesCleared", gamesCleared));
    }

    /**
     * Clears every active game while leaving the active scenario unchanged.
     * Requires MANAGE_GAME (same capability that chooses the active game).
     */
    @Transactional
    public void deactivateGames() {
        access.requireFunctionality(FunctionalityCodes.MANAGE_GAME);
        int gamesCleared = clearActiveGames();
        activation.reconcile();
        log.info("games.deactivated count={}", gamesCleared);
        audit.record("DEACTIVATE", "Game", null, "No active game", Map.of("gamesCleared", gamesCleared));
    }

    // ---- games

    @Transactional(readOnly = true)
    public List<MoonPhase> moonPhases() {
        access.requireFunctionality(FunctionalityCodes.MANAGE_GAME);
        return List.of(MoonPhase.values());
    }

    @Transactional(readOnly = true)
    public List<Game> listGames(Long scenarioId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_GAME);
        findScenario(scenarioId);
        return games.findByScenarioIdOrderByNameAsc(scenarioId);
    }

    @Transactional(readOnly = true)
    public Game getGame(Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_GAME);
        return findGame(id);
    }

    @Transactional
    public Game createGame(Long scenarioId, String name, LocalDateTime dateTime, LocalTime sunrise, LocalTime sunset,
            String moonphases, String description, Map<String, Object> metadata) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_GAME);
        findScenario(scenarioId);
        Game game = new Game();
        game.setScenarioId(scenarioId);
        applyGameFields(game, name, dateTime, sunrise, sunset, moonphases, description, metadata);
        Game saved = games.save(game);
        log.info("game.created id={} scenarioId={}", saved.getId(), scenarioId);
        audit.record("CREATE", "Game", saved.getId(), "Game " + saved.getName() + " created",
                Map.of("scenarioId", scenarioId));
        return saved;
    }

    @Transactional
    public Game updateGame(Long id, String name, LocalDateTime dateTime, LocalTime sunrise, LocalTime sunset,
            String moonphases, String description, Map<String, Object> metadata) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_GAME);
        Game game = findGame(id);
        applyGameFields(game, name, dateTime, sunrise, sunset, moonphases, description, metadata);
        Game saved = games.save(game);
        log.info("game.updated id={}", saved.getId());
        audit.record("UPDATE", "Game", saved.getId(), "Game " + saved.getName() + " updated", Map.of());
        return saved;
    }

    @Transactional
    public void deleteGame(Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_GAME);
        Game game = findGame(id);
        if (game.isActive()) {
            log.warn("game.delete_rejected id={} reason=active", id);
            throw new ConflictException("The active game cannot be deleted.");
        }
        purgeGame(game);
        activation.reconcile();
        log.info("game.deleted id={}", id);
        audit.record("DELETE", "Game", id, "Game " + game.getName() + " deleted", Map.of());
    }

    /** Makes the game the only active one and activates its scenario too. */
    @Transactional
    public Game activateGame(Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_GAME);
        Game game = findGame(id);
        List<PlayingOrbat> owned = playingOrbats.findByGameId(game.getId());
        if (owned.isEmpty()) {
            log.warn("game.activate_rejected id={} reason=no_playing_orbat", id);
            throw new BadRequestException("This game has no playing ORBAT.");
        }
        if (owned.stream().anyMatch(orbat -> !ActivationService.isFinalized(orbat))) {
            log.warn("game.activate_rejected id={} reason=draft_playing_orbat", id);
            throw new BadRequestException("Finalize every playing ORBAT of this game before activating it.");
        }
        Scenario scenario = findScenario(game.getScenarioId());
        activate(scenario);
        for (Game other : games.findByActiveTrue()) {
            if (!other.getId().equals(id)) {
                other.setActive(false);
                games.save(other);
            }
        }
        game.setActive(true);
        Game saved = games.save(game);
        activation.reconcile();
        log.info("game.activated id={} scenarioId={}", id, scenario.getId());
        audit.record("ACTIVATE", "Game", id, "Game " + game.getName() + " activated",
                Map.of("scenarioId", scenario.getId()));
        return saved;
    }

    // ---- shared

    /**
     * Removes a game with everything it owns: its playing ORBATs (assigned users are unassigned) and its
     * alliance sides with their members.
     */
    private void purgeGame(Game game) {
        for (PlayingOrbat orbat : playingOrbats.findByGameId(game.getId())) {
            for (AppUser assigned : users.findByPlayingOrbatId(orbat.getId())) {
                assigned.setPlayingOrbatId(null);
                assigned.setPlayingNodeId(null);
                users.save(assigned);
            }
            playingOrbats.delete(orbat);
        }
        for (Side alliance : sides.findByGameId(game.getId())) {
            List<AllianceMember> members = allianceMembers.findByAllianceSideId(alliance.getId());
            allianceMembers.deleteAll(members);
            sides.delete(alliance);
        }
        games.delete(game);
    }

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
        // Choosing a scenario always clears the active game; the user picks a game next.
        clearActiveGames();
        scenario.setActive(true);
        scenarios.save(scenario);
    }

    private int clearActiveGames() {
        int cleared = 0;
        for (Game game : games.findByActiveTrue()) {
            game.setActive(false);
            games.save(game);
            cleared++;
        }
        return cleared;
    }

    private static void applyScenarioFields(Scenario scenario, String name, String generalIdea,
            String openingNarrative, String specialIdea) {
        scenario.setName(name.trim());
        scenario.setGeneralIdea(blankToNull(generalIdea));
        scenario.setOpeningNarrative(blankToNull(openingNarrative));
        scenario.setSpecialIdea(blankToNull(specialIdea));
    }

    private static void applyGameFields(Game game, String name, LocalDateTime dateTime, LocalTime sunrise,
            LocalTime sunset, String moonphases, String description, Map<String, Object> metadata) {
        game.setName(name.trim());
        game.setDateTime(dateTime);
        game.setSunrise(sunrise);
        game.setSunset(sunset);
        game.setMoonphases(moonPhase(moonphases));
        game.setDescription(blankToNull(description));
        game.setMetadata(metadata);
    }

    private static MoonPhase moonPhase(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return MoonPhase.parse(raw).orElseThrow(() -> new BadRequestException("Choose a moon phase from the list."));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
