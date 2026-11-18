package com.gis.warplan.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.Game;
import com.gis.warplan.domain.PlayingOrbat;
import com.gis.warplan.domain.Scenario;
import com.gis.warplan.repository.GameRepository;
import com.gis.warplan.repository.PlayingOrbatRepository;
import com.gis.warplan.repository.ScenarioRepository;

/**
 * Decides whether a user may work (see the map and play). An administrator always may. Anyone else needs an
 * active scenario, an active game of that scenario, and a finalized playing ORBAT of that active game
 * assigned to them.
 */
@Service
public class ActivationService {

    public static final String NO_ACTIVE_SCENARIO = "NO_ACTIVE_SCENARIO";
    public static final String NO_ACTIVE_GAME = "NO_ACTIVE_GAME";
    public static final String NOT_ASSIGNED = "NOT_ASSIGNED";
    public static final String ORBAT_NOT_FINALIZED = "ORBAT_NOT_FINALIZED";

    private final ScenarioRepository scenarios;
    private final GameRepository games;
    private final PlayingOrbatRepository playingOrbats;
    private final AccessService access;

    public ActivationService(ScenarioRepository scenarios, GameRepository games, PlayingOrbatRepository playingOrbats,
            AccessService access) {
        this.scenarios = scenarios;
        this.games = games;
        this.playingOrbats = playingOrbats;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Activation evaluate(AppUser user) {
        if (access.isAdministrator(user)) {
            return new Activation(true, null);
        }
        List<Scenario> activeScenarios = scenarios.findByActiveTrue();
        if (activeScenarios.isEmpty()) {
            return new Activation(false, NO_ACTIVE_SCENARIO);
        }
        Scenario scenario = activeScenarios.get(0);
        Optional<Game> activeGame = games.findByActiveTrue().stream()
                .filter(game -> scenario.getId().equals(game.getScenarioId()))
                .findFirst();
        if (activeGame.isEmpty()) {
            return new Activation(false, NO_ACTIVE_GAME);
        }
        Long activeGameId = activeGame.get().getId();
        Optional<PlayingOrbat> orbat = user.getPlayingOrbatId() == null
                ? Optional.empty()
                : playingOrbats.findById(user.getPlayingOrbatId())
                        .filter(found -> activeGameId.equals(found.getGameId()));
        if (orbat.isEmpty()) {
            return new Activation(false, NOT_ASSIGNED);
        }
        if (!isFinalized(orbat.get())) {
            return new Activation(false, ORBAT_NOT_FINALIZED);
        }
        return new Activation(true, null);
    }

    public static boolean isFinalized(PlayingOrbat orbat) {
        return orbat.getMetadata() != null
                && OrbatSupport.STATUS_FINALIZED.equals(orbat.getMetadata().get("status"));
    }

    /** {@code reason} is null when the user can work. */
    public record Activation(boolean canWork, String reason) {
    }
}
