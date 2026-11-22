package com.gis.warplan.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.Game;
import com.gis.warplan.domain.PlayingOrbat;
import com.gis.warplan.domain.Scenario;
import com.gis.warplan.domain.UserSession;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.GameRepository;
import com.gis.warplan.repository.PlayingOrbatRepository;
import com.gis.warplan.repository.ScenarioRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.repository.UserSessionRepository;

/**
 * Decides whether a marked user may sign in and work. An administrator who was not marked from a standard
 * node always may. A marked user needs a finalized playing node of the active game, and that node must not
 * be held out of the game.
 */
@Service
public class ActivationService {

    public static final String NO_ACTIVE_SCENARIO = "NO_ACTIVE_SCENARIO";
    public static final String NO_ACTIVE_GAME = "NO_ACTIVE_GAME";
    public static final String NOT_ASSIGNED = "NOT_ASSIGNED";
    public static final String ORBAT_NOT_FINALIZED = "ORBAT_NOT_FINALIZED";

    private static final Logger log = LogManager.getLogger(ActivationService.class);

    private final ScenarioRepository scenarios;
    private final GameRepository games;
    private final PlayingOrbatRepository playingOrbats;
    private final AppUserRepository users;
    private final SideRepository sides;
    private final UserSessionRepository sessions;
    private final AccessService access;

    public ActivationService(ScenarioRepository scenarios, GameRepository games, PlayingOrbatRepository playingOrbats,
            AppUserRepository users, SideRepository sides, UserSessionRepository sessions, AccessService access) {
        this.scenarios = scenarios;
        this.games = games;
        this.playingOrbats = playingOrbats;
        this.users = users;
        this.sides = sides;
        this.sessions = sessions;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Activation evaluate(AppUser user) {
        if (user.getStandardNodeId() == null || user.getStandardNodeId().isBlank()) {
            if (access.isAdministrator(user)) {
                return new Activation(true, null);
            }
            return evaluateUnmarked(user);
        }
        if (activeScenario().isEmpty()) {
            return new Activation(false, NO_ACTIVE_SCENARIO);
        }
        if (activeGame().isEmpty()) {
            return new Activation(false, NO_ACTIVE_GAME);
        }
        if (locate(user.getStandardNodeId(), finalizedOfActiveGame(), new HashMap<>()).isEmpty()) {
            return new Activation(false, NOT_ASSIGNED);
        }
        return new Activation(true, null);
    }

    /**
     * Rewrites {@code active} and the playing link for every user marked from a standard node. Other accounts
     * are left alone. A user who loses {@code active} has open sessions revoked.
     */
    @Transactional
    public void reconcile() {
        List<AppUser> marked = users.findByStandardNodeIdIsNotNull();
        List<PlayingOrbat> finalized = finalizedOfActiveGame();
        Map<Long, Boolean> allianceBySide = new HashMap<>();
        int activated = 0;
        int cleared = 0;
        for (AppUser user : marked) {
            Optional<Seat> seat = locate(user.getStandardNodeId(), finalized, allianceBySide);
            boolean wasActive = user.isActive();
            if (seat.isPresent()) {
                Seat found = seat.get();
                boolean changed = !wasActive || !found.orbatId().equals(user.getPlayingOrbatId())
                        || !found.nodeId().equals(user.getPlayingNodeId());
                if (!changed) {
                    continue;
                }
                user.setActive(true);
                user.setPlayingOrbatId(found.orbatId());
                user.setPlayingNodeId(found.nodeId());
                users.save(user);
                activated++;
            } else if (wasActive || user.getPlayingOrbatId() != null || user.getPlayingNodeId() != null) {
                user.setActive(false);
                user.setPlayingOrbatId(null);
                user.setPlayingNodeId(null);
                users.save(user);
                if (wasActive) {
                    revokeSessions(user.getId());
                }
                cleared++;
            }
        }
        log.info("participation.reconciled marked={} activated={} cleared={}", marked.size(), activated, cleared);
    }

    /** The active scenario, when one is selected. */
    @Transactional(readOnly = true)
    public Optional<Scenario> activeScenario() {
        List<Scenario> active = scenarios.findByActiveTrue();
        return active.isEmpty() ? Optional.empty() : Optional.of(active.get(0));
    }

    /** The active game of the active scenario. Empty when either is missing. */
    @Transactional(readOnly = true)
    public Optional<Game> activeGame() {
        Optional<Scenario> scenario = activeScenario();
        if (scenario.isEmpty()) {
            return Optional.empty();
        }
        Long scenarioId = scenario.get().getId();
        return games.findByActiveTrue().stream()
                .filter(game -> scenarioId.equals(game.getScenarioId()))
                .findFirst();
    }

    public static boolean isFinalized(PlayingOrbat orbat) {
        return orbat.getMetadata() != null
                && OrbatSupport.STATUS_FINALIZED.equals(orbat.getMetadata().get("status"));
    }

    private Activation evaluateUnmarked(AppUser user) {
        if (activeScenario().isEmpty()) {
            return new Activation(false, NO_ACTIVE_SCENARIO);
        }
        Optional<Game> activeGame = activeGame();
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

    private List<PlayingOrbat> finalizedOfActiveGame() {
        return activeGame()
                .map(game -> playingOrbats.findByGameId(game.getId()).stream().filter(ActivationService::isFinalized).toList())
                .orElse(List.of());
    }

    /**
     * A country playing ORBAT wins over an alliance that also copied the same standard node. A held node
     * does not qualify, and a held country node is not replaced by the alliance copy.
     */
    private Optional<Seat> locate(String standardNodeId, List<PlayingOrbat> finalized, Map<Long, Boolean> allianceBySide) {
        Seat country = null;
        Seat alliance = null;
        for (PlayingOrbat orbat : finalized) {
            Map<String, Object> node = nodeFor(orbat, standardNodeId);
            if (node == null || !(node.get("id") instanceof String nodeId)) {
                continue;
            }
            Seat seat = new Seat(orbat.getId(), nodeId, Boolean.TRUE.equals(node.get(OrbatSupport.PARTICIPATION_HELD)));
            if (isAlliance(orbat, allianceBySide)) {
                if (alliance == null) {
                    alliance = seat;
                }
            } else {
                country = seat;
            }
        }
        Seat chosen = country != null ? country : alliance;
        if (chosen == null || chosen.held()) {
            return Optional.empty();
        }
        return Optional.of(chosen);
    }

    private static Map<String, Object> nodeFor(PlayingOrbat orbat, String standardNodeId) {
        for (Map<String, Object> node : OrbatSupport.nodesOf(orbat.getOrbatJson())) {
            if (standardNodeId.equals(node.get("sourceNodeId"))) {
                return node;
            }
        }
        return null;
    }

    private boolean isAlliance(PlayingOrbat orbat, Map<Long, Boolean> allianceBySide) {
        Long sideId = orbat.getSideId();
        if (sideId == null) {
            return false;
        }
        return allianceBySide.computeIfAbsent(sideId, id -> sides.findById(id)
                .map(side -> SideService.KIND_ALLIANCE.equals(side.getKind()))
                .orElse(false));
    }

    private void revokeSessions(Long userId) {
        Instant now = Instant.now();
        for (UserSession session : sessions.findByUserId(userId)) {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(now);
                sessions.save(session);
            }
        }
    }

    /** {@code reason} is null when the user can work. */
    public record Activation(boolean canWork, String reason) {
    }

    private record Seat(Long orbatId, String nodeId, boolean held) {
    }
}
