package com.drdo.jayant.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drdo.jayant.domain.AllianceMember;
import com.drdo.jayant.domain.AppUser;
import com.drdo.jayant.domain.FunctionalityCodes;
import com.drdo.jayant.domain.Game;
import com.drdo.jayant.domain.PlayingOrbat;
import com.drdo.jayant.domain.StandardOrbat;
import com.drdo.jayant.domain.Side;
import com.drdo.jayant.repository.AllianceMemberRepository;
import com.drdo.jayant.repository.AppUserRepository;
import com.drdo.jayant.repository.GameRepository;
import com.drdo.jayant.repository.PlayingOrbatRepository;
import com.drdo.jayant.repository.StandardOrbatRepository;
import com.drdo.jayant.repository.SideRepository;
import com.drdo.jayant.security.UserPrincipal;
import com.drdo.jayant.web.BadRequestException;
import com.drdo.jayant.web.ConflictException;
import com.drdo.jayant.web.ForbiddenException;
import com.drdo.jayant.web.NotFoundException;

/** Playing ORBATs of a game: replicas of standard ORBATs, alliance ORBATs, editing and finalizing. */
@Service
public class PlayingOrbatService {

    private final PlayingOrbatRepository playingOrbats;
    private final StandardOrbatRepository standardOrbats;
    private final GameRepository games;
    private final SideRepository sides;
    private final AllianceMemberRepository allianceMembers;
    private final AppUserRepository users;
    private final SideService sideService;
    private final OrbatSupport support;
    private final AccessService access;
    private final AuditService audit;

    public PlayingOrbatService(PlayingOrbatRepository playingOrbats, StandardOrbatRepository standardOrbats,
            GameRepository games, SideRepository sides, AllianceMemberRepository allianceMembers,
            AppUserRepository users, SideService sideService, OrbatSupport support, AccessService access,
            AuditService audit) {
        this.playingOrbats = playingOrbats;
        this.standardOrbats = standardOrbats;
        this.games = games;
        this.sides = sides;
        this.allianceMembers = allianceMembers;
        this.users = users;
        this.sideService = sideService;
        this.support = support;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PlayingOrbat> list(Long gameId) {
        access.requireAnyFunctionality(FunctionalityCodes.MANAGE_PLAYING_ORBAT, FunctionalityCodes.MANAGE_ALLIANCE);
        requireGame(gameId);
        return playingOrbats.findByGameId(gameId);
    }

    /** Readable by ORBAT managers and by the users the ORBAT is assigned to. */
    @Transactional(readOnly = true)
    public PlayingOrbat get(Long gameId, Long id) {
        UserPrincipal principal = access.requirePrincipal();
        requireGame(gameId);
        PlayingOrbat orbat = find(gameId, id);
        boolean manager = access.isAdministrator() || principal.hasFunctionality(FunctionalityCodes.MANAGE_PLAYING_ORBAT)
                || principal.hasFunctionality(FunctionalityCodes.MANAGE_ALLIANCE);
        if (!manager) {
            boolean assigned = users.findById(principal.userId())
                    .map(user -> id.equals(user.getPlayingOrbatId())).orElse(false);
            if (!assigned) {
                throw new ForbiddenException("You do not have access to this playing ORBAT.");
            }
        }
        return orbat;
    }

    @Transactional
    public PlayingOrbat replicate(Long gameId, Long sideId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_PLAYING_ORBAT);
        Game game = requireGame(gameId);
        Side side = sides.findById(sideId).orElseThrow(() -> new NotFoundException("Side not found."));
        access.requireSideScope(sideId);
        if (!SideService.KIND_COUNTRY.equals(side.getKind())) {
            throw new BadRequestException("Use the alliance endpoint to create an alliance ORBAT.");
        }
        StandardOrbat standard = standardOrbat(side);
        if (playingOrbats.findFirstByGameIdAndSideId(gameId, sideId).isPresent()) {
            throw new ConflictException("This side already has a playing ORBAT in the game.");
        }
        PlayingOrbat orbat = new PlayingOrbat();
        orbat.setName(side.getName());
        orbat.setSideId(sideId);
        orbat.setGameId(game.getId());
        orbat.setScenarioId(game.getScenarioId());
        orbat.setStandardOrbatId(standard.getId());
        orbat.setOrbatJson(OrbatSupport.document(OrbatSupport.replicate(OrbatSupport.nodesOf(standard.getOrbatJson()),
                null)));
        orbat.setMetadata(draftMetadata());
        PlayingOrbat saved = playingOrbats.saveAndFlush(orbat);
        audit.record("CREATE", "PlayingOrbat", saved.getId(), "Playing ORBAT replicated for " + side.getName(),
                Map.of("gameId", gameId, "scenarioId", String.valueOf(game.getScenarioId()), "sideId", sideId));
        return saved;
    }

    @Transactional
    public PlayingOrbat update(Long gameId, Long id, Map<String, Object> orbatJson, Map<String, Object> metadata,
            Long version) {
        access.requireAnyFunctionality(FunctionalityCodes.MANAGE_PLAYING_ORBAT, FunctionalityCodes.MANAGE_ALLIANCE);
        Game game = requireGame(gameId);
        PlayingOrbat orbat = find(gameId, id);
        if (!Objects.equals(orbat.getVersion(), version)) {
            throw new ConflictException("The playing ORBAT was changed by someone else. Reload and try again.");
        }
        Map<String, Object> merged = new LinkedHashMap<>(orbat.getMetadata() == null ? Map.of() : orbat.getMetadata());
        if (metadata != null) {
            merged.putAll(metadata);
        }
        String status = status(merged);
        merged.put("status", status);
        if (orbatJson != null) {
            support.validate(orbatJson);
            orbat.setOrbatJson(orbatJson);
        }
        orbat.setMetadata(merged);
        orbat.setScenarioId(game.getScenarioId());
        PlayingOrbat saved = playingOrbats.saveAndFlush(orbat);
        int assigned = 0;
        if (OrbatSupport.STATUS_FINALIZED.equals(status)) {
            support.validate(saved.getOrbatJson());
            assigned = assignUsers(saved);
        }
        audit.record(OrbatSupport.STATUS_FINALIZED.equals(status) ? "FINALIZE" : "UPDATE", "PlayingOrbat",
                saved.getId(), "Playing ORBAT " + saved.getName() + " saved as " + status,
                Map.of("gameId", gameId, "scenarioId", String.valueOf(saved.getScenarioId()), "status", status, "version",
                        saved.getVersion(), "usersAssigned", assigned));
        return saved;
    }

    @Transactional
    public void delete(Long gameId, Long id) {
        access.requireAnyFunctionality(FunctionalityCodes.MANAGE_PLAYING_ORBAT, FunctionalityCodes.MANAGE_ALLIANCE);
        requireGame(gameId);
        PlayingOrbat orbat = find(gameId, id);
        for (AppUser assigned : users.findByPlayingOrbatId(id)) {
            assigned.setPlayingOrbatId(null);
            assigned.setPlayingNodeId(null);
            users.save(assigned);
        }
        playingOrbats.delete(orbat);
        audit.record("DELETE", "PlayingOrbat", id, "Playing ORBAT " + orbat.getName() + " deleted", Map.of());
    }

    // ---- alliances

    @Transactional
    public Alliance createAlliance(Long gameId, String name, String colour, String icon, Long themeId,
            List<Long> memberSideIds) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ALLIANCE);
        Game game = requireGame(gameId);
        Set<Long> members = new LinkedHashSet<>(memberSideIds == null ? List.of() : memberSideIds);
        if (members.isEmpty() || members.contains(null)) {
            throw new BadRequestException("memberSideIds must list at least one side.");
        }
        List<Side> memberSides = new ArrayList<>();
        for (Long memberId : members) {
            Side member = sides.findById(memberId)
                    .orElseThrow(() -> new BadRequestException("Side " + memberId + " does not exist."));
            if (!SideService.KIND_COUNTRY.equals(member.getKind())) {
                throw new BadRequestException("Side " + member.getName() + " is not a country side.");
            }
            memberSides.add(member);
        }
        Map<Long, StandardOrbat> standards = new HashMap<>();
        for (Side member : memberSides) {
            standards.put(member.getId(), standardOrbat(member));
        }

        Side allianceDraft = new Side();
        allianceDraft.setGameId(game.getId());
        allianceDraft.setScenarioId(game.getScenarioId());
        Side alliance = sides.saveAndFlush(sideService.build(allianceDraft, SideService.KIND_ALLIANCE, name,
                "Alliance of " + String.join(", ", memberSides.stream().map(Side::getName).toList()), colour, icon,
                themeId));
        for (Long memberId : members) {
            AllianceMember row = new AllianceMember();
            row.setAllianceSideId(alliance.getId());
            row.setMemberSideId(memberId);
            allianceMembers.save(row);
        }

        Map<String, Object> root = OrbatSupport.rootNode(alliance.getName(), alliance.getId());
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(root);
        for (Side member : memberSides) {
            nodes.addAll(OrbatSupport.replicate(OrbatSupport.nodesOf(standards.get(member.getId()).getOrbatJson()),
                    (String) root.get("id")));
        }
        PlayingOrbat orbat = new PlayingOrbat();
        orbat.setName(alliance.getName());
        orbat.setSideId(alliance.getId());
        orbat.setGameId(game.getId());
        orbat.setScenarioId(game.getScenarioId());
        orbat.setOrbatJson(OrbatSupport.document(nodes));
        Map<String, Object> metadata = draftMetadata();
        metadata.put("alliance", true);
        metadata.put("memberSideIds", new ArrayList<>(members));
        orbat.setMetadata(metadata);
        support.validate(orbat.getOrbatJson());
        PlayingOrbat savedOrbat = playingOrbats.saveAndFlush(orbat);

        audit.record("CREATE", "Alliance", alliance.getId(), "Alliance " + alliance.getName() + " created",
                Map.of("gameId", gameId, "scenarioId", String.valueOf(game.getScenarioId()), "memberSideIds", new ArrayList<>(members), "playingOrbatId",
                        savedOrbat.getId()));
        return new Alliance(alliance, new ArrayList<>(members), savedOrbat);
    }

    @Transactional(readOnly = true)
    public List<Alliance> listAlliances(Long gameId) {
        access.requireAnyFunctionality(FunctionalityCodes.MANAGE_ALLIANCE, FunctionalityCodes.MANAGE_PLAYING_ORBAT);
        requireGame(gameId);
        List<Alliance> result = new ArrayList<>();
        for (PlayingOrbat orbat : playingOrbats.findByGameId(gameId)) {
            Side side = orbat.getSideId() == null ? null : sides.findById(orbat.getSideId()).orElse(null);
            if (side != null && SideService.KIND_ALLIANCE.equals(side.getKind())) {
                List<Long> memberIds = allianceMembers.findByAllianceSideId(side.getId()).stream()
                        .map(AllianceMember::getMemberSideId).toList();
                result.add(new Alliance(side, memberIds, orbat));
            }
        }
        return result;
    }

    // ---- helpers

    /**
     * Links users to playing nodes: a user whose standardNodeId equals a node's sourceNodeId receives that
     * playing ORBAT and node. An alliance ORBAT never takes over a user who already plays in another ORBAT of
     * the same game (their own country's one).
     */
    private int assignUsers(PlayingOrbat orbat) {
        Map<String, String> bySource = new HashMap<>();
        for (Map<String, Object> node : OrbatSupport.nodesOf(orbat.getOrbatJson())) {
            if (node.get("sourceNodeId") instanceof String source && node.get("id") instanceof String id) {
                bySource.put(source, id);
            }
        }
        boolean alliance = orbat.getSideId() != null && sides.findById(orbat.getSideId())
                .map(side -> SideService.KIND_ALLIANCE.equals(side.getKind())).orElse(false);
        int assigned = 0;
        for (AppUser user : users.findByStandardNodeIdIsNotNull()) {
            String playingNode = bySource.get(user.getStandardNodeId());
            if (playingNode == null) {
                continue;
            }
            if (alliance && playsElsewhere(user, orbat)) {
                continue;
            }
            user.setPlayingOrbatId(orbat.getId());
            user.setPlayingNodeId(playingNode);
            users.save(user);
            assigned++;
        }
        return assigned;
    }

    private boolean playsElsewhere(AppUser user, PlayingOrbat orbat) {
        if (user.getPlayingOrbatId() == null || user.getPlayingOrbatId().equals(orbat.getId())) {
            return false;
        }
        return playingOrbats.findById(user.getPlayingOrbatId())
                .map(other -> Objects.equals(other.getGameId(), orbat.getGameId()))
                .orElse(false);
    }

    private StandardOrbat standardOrbat(Side side) {
        List<StandardOrbat> found = standardOrbats.findBySideId(side.getId());
        if (found.isEmpty()) {
            throw new BadRequestException("Side " + side.getName() + " has no standard ORBAT.");
        }
        return found.get(0);
    }

    private static String status(Map<String, Object> metadata) {
        Object raw = metadata.get("status");
        if (raw == null) {
            return OrbatSupport.STATUS_DRAFT;
        }
        String value = raw instanceof String text ? text.trim().toLowerCase(Locale.ROOT) : "";
        if (!value.equals(OrbatSupport.STATUS_DRAFT) && !value.equals(OrbatSupport.STATUS_FINALIZED)) {
            throw new BadRequestException("metadata.status must be draft or finalized.");
        }
        return value;
    }

    private static Map<String, Object> draftMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", OrbatSupport.STATUS_DRAFT);
        return metadata;
    }

    /** Finds the playing ORBAT and checks that it belongs to the game in the path. */
    private PlayingOrbat find(Long gameId, Long id) {
        return playingOrbats.findById(id).filter(orbat -> gameId.equals(orbat.getGameId()))
                .orElseThrow(() -> new NotFoundException("Playing ORBAT not found."));
    }

    private Game requireGame(Long gameId) {
        return games.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found."));
    }

    public record Alliance(Side side, List<Long> memberSideIds, PlayingOrbat playingOrbat) {
    }
}
