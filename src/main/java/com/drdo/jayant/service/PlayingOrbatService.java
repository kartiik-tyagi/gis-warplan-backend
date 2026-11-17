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
import com.drdo.jayant.domain.FeatureCodes;
import com.drdo.jayant.domain.PlayingOrbat;
import com.drdo.jayant.domain.StandardOrbat;
import com.drdo.jayant.domain.Team;
import com.drdo.jayant.repository.AllianceMemberRepository;
import com.drdo.jayant.repository.AppUserRepository;
import com.drdo.jayant.repository.PlayingOrbatRepository;
import com.drdo.jayant.repository.ScenarioRepository;
import com.drdo.jayant.repository.StandardOrbatRepository;
import com.drdo.jayant.repository.TeamRepository;
import com.drdo.jayant.security.UserPrincipal;
import com.drdo.jayant.web.BadRequestException;
import com.drdo.jayant.web.ConflictException;
import com.drdo.jayant.web.ForbiddenException;
import com.drdo.jayant.web.NotFoundException;

/** Playing ORBATs of a scenario: replicas of standard ORBATs, alliance ORBATs, editing and finalizing. */
@Service
public class PlayingOrbatService {

    private final PlayingOrbatRepository playingOrbats;
    private final StandardOrbatRepository standardOrbats;
    private final ScenarioRepository scenarios;
    private final TeamRepository teams;
    private final AllianceMemberRepository allianceMembers;
    private final AppUserRepository users;
    private final TeamService teamService;
    private final OrbatSupport support;
    private final AccessService access;
    private final AuditService audit;

    public PlayingOrbatService(PlayingOrbatRepository playingOrbats, StandardOrbatRepository standardOrbats,
            ScenarioRepository scenarios, TeamRepository teams, AllianceMemberRepository allianceMembers,
            AppUserRepository users, TeamService teamService, OrbatSupport support, AccessService access,
            AuditService audit) {
        this.playingOrbats = playingOrbats;
        this.standardOrbats = standardOrbats;
        this.scenarios = scenarios;
        this.teams = teams;
        this.allianceMembers = allianceMembers;
        this.users = users;
        this.teamService = teamService;
        this.support = support;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PlayingOrbat> list(Long scenarioId) {
        access.requireAnyFeature(FeatureCodes.MANAGE_PLAYING_ORBAT, FeatureCodes.MANAGE_ALLIANCE);
        requireScenario(scenarioId);
        return playingOrbats.findByScenarioId(scenarioId);
    }

    /** Readable by ORBAT managers and by the users the ORBAT is assigned to. */
    @Transactional(readOnly = true)
    public PlayingOrbat get(Long id) {
        UserPrincipal principal = access.requirePrincipal();
        PlayingOrbat orbat = find(id);
        boolean manager = access.isAdministrator() || principal.hasFeature(FeatureCodes.MANAGE_PLAYING_ORBAT)
                || principal.hasFeature(FeatureCodes.MANAGE_ALLIANCE);
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
    public PlayingOrbat replicate(Long scenarioId, Long teamId) {
        access.requireFeature(FeatureCodes.MANAGE_PLAYING_ORBAT);
        requireScenario(scenarioId);
        Team team = teams.findById(teamId).orElseThrow(() -> new NotFoundException("Team not found."));
        access.requireTeamScope(teamId);
        if (!TeamService.KIND_COUNTRY.equals(team.getKind())) {
            throw new BadRequestException("Use the alliance endpoint to create an alliance ORBAT.");
        }
        StandardOrbat standard = standardOrbat(team);
        if (playingOrbats.findFirstByScenarioIdAndTeamId(scenarioId, teamId).isPresent()) {
            throw new ConflictException("This team already has a playing ORBAT in the scenario.");
        }
        PlayingOrbat orbat = new PlayingOrbat();
        orbat.setName(team.getName());
        orbat.setTeamId(teamId);
        orbat.setScenarioId(scenarioId);
        orbat.setStandardOrbatId(standard.getId());
        orbat.setOrbatJson(OrbatSupport.document(OrbatSupport.replicate(OrbatSupport.nodesOf(standard.getOrbatJson()),
                null)));
        orbat.setMetadata(draftMetadata());
        PlayingOrbat saved = playingOrbats.saveAndFlush(orbat);
        audit.record("CREATE", "PlayingOrbat", saved.getId(), "Playing ORBAT replicated for " + team.getName(),
                Map.of("scenarioId", scenarioId, "teamId", teamId));
        return saved;
    }

    @Transactional
    public PlayingOrbat update(Long id, Map<String, Object> orbatJson, Map<String, Object> metadata, Long version) {
        access.requireAnyFeature(FeatureCodes.MANAGE_PLAYING_ORBAT, FeatureCodes.MANAGE_ALLIANCE);
        PlayingOrbat orbat = find(id);
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
        PlayingOrbat saved = playingOrbats.saveAndFlush(orbat);
        int assigned = 0;
        if (OrbatSupport.STATUS_FINALIZED.equals(status)) {
            support.validate(saved.getOrbatJson());
            assigned = assignUsers(saved);
        }
        audit.record(OrbatSupport.STATUS_FINALIZED.equals(status) ? "FINALIZE" : "UPDATE", "PlayingOrbat",
                saved.getId(), "Playing ORBAT " + saved.getName() + " saved as " + status,
                Map.of("scenarioId", String.valueOf(saved.getScenarioId()), "status", status, "version",
                        saved.getVersion(), "usersAssigned", assigned));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        access.requireAnyFeature(FeatureCodes.MANAGE_PLAYING_ORBAT, FeatureCodes.MANAGE_ALLIANCE);
        PlayingOrbat orbat = find(id);
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
    public Alliance createAlliance(Long scenarioId, String name, String colour, String icon, Long themeId,
            List<Long> memberTeamIds) {
        access.requireFeature(FeatureCodes.MANAGE_ALLIANCE);
        requireScenario(scenarioId);
        Set<Long> members = new LinkedHashSet<>(memberTeamIds == null ? List.of() : memberTeamIds);
        if (members.isEmpty() || members.contains(null)) {
            throw new BadRequestException("memberTeamIds must list at least one team.");
        }
        List<Team> memberTeams = new ArrayList<>();
        for (Long memberId : members) {
            Team member = teams.findById(memberId)
                    .orElseThrow(() -> new BadRequestException("Team " + memberId + " does not exist."));
            if (!TeamService.KIND_COUNTRY.equals(member.getKind())) {
                throw new BadRequestException("Team " + member.getName() + " is not a country team.");
            }
            memberTeams.add(member);
        }
        Map<Long, StandardOrbat> standards = new HashMap<>();
        for (Team member : memberTeams) {
            standards.put(member.getId(), standardOrbat(member));
        }

        Team alliance = teams.saveAndFlush(teamService.build(new Team(), TeamService.KIND_ALLIANCE, name,
                "Alliance of " + String.join(", ", memberTeams.stream().map(Team::getName).toList()), colour, icon,
                themeId));
        for (Long memberId : members) {
            AllianceMember row = new AllianceMember();
            row.setAllianceTeamId(alliance.getId());
            row.setMemberTeamId(memberId);
            allianceMembers.save(row);
        }

        Map<String, Object> root = OrbatSupport.rootNode(alliance.getName(), alliance.getId());
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(root);
        for (Team member : memberTeams) {
            nodes.addAll(OrbatSupport.replicate(OrbatSupport.nodesOf(standards.get(member.getId()).getOrbatJson()),
                    (String) root.get("id")));
        }
        PlayingOrbat orbat = new PlayingOrbat();
        orbat.setName(alliance.getName());
        orbat.setTeamId(alliance.getId());
        orbat.setScenarioId(scenarioId);
        orbat.setOrbatJson(OrbatSupport.document(nodes));
        Map<String, Object> metadata = draftMetadata();
        metadata.put("alliance", true);
        metadata.put("memberTeamIds", new ArrayList<>(members));
        orbat.setMetadata(metadata);
        support.validate(orbat.getOrbatJson());
        PlayingOrbat savedOrbat = playingOrbats.saveAndFlush(orbat);

        audit.record("CREATE", "Alliance", alliance.getId(), "Alliance " + alliance.getName() + " created",
                Map.of("scenarioId", scenarioId, "memberTeamIds", new ArrayList<>(members), "playingOrbatId",
                        savedOrbat.getId()));
        return new Alliance(alliance, new ArrayList<>(members), savedOrbat);
    }

    @Transactional(readOnly = true)
    public List<Alliance> listAlliances(Long scenarioId) {
        access.requireAnyFeature(FeatureCodes.MANAGE_ALLIANCE, FeatureCodes.MANAGE_PLAYING_ORBAT);
        requireScenario(scenarioId);
        List<Alliance> result = new ArrayList<>();
        for (PlayingOrbat orbat : playingOrbats.findByScenarioId(scenarioId)) {
            Team team = orbat.getTeamId() == null ? null : teams.findById(orbat.getTeamId()).orElse(null);
            if (team != null && TeamService.KIND_ALLIANCE.equals(team.getKind())) {
                List<Long> memberIds = allianceMembers.findByAllianceTeamId(team.getId()).stream()
                        .map(AllianceMember::getMemberTeamId).toList();
                result.add(new Alliance(team, memberIds, orbat));
            }
        }
        return result;
    }

    // ---- helpers

    /**
     * Links users to playing nodes: a user whose standardNodeId equals a node's sourceNodeId receives that
     * playing ORBAT and node. An alliance ORBAT never takes over a user who already plays in another ORBAT of
     * the same scenario (their own country's one).
     */
    private int assignUsers(PlayingOrbat orbat) {
        Map<String, String> bySource = new HashMap<>();
        for (Map<String, Object> node : OrbatSupport.nodesOf(orbat.getOrbatJson())) {
            if (node.get("sourceNodeId") instanceof String source && node.get("id") instanceof String id) {
                bySource.put(source, id);
            }
        }
        boolean alliance = orbat.getTeamId() != null && teams.findById(orbat.getTeamId())
                .map(team -> TeamService.KIND_ALLIANCE.equals(team.getKind())).orElse(false);
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
                .map(other -> Objects.equals(other.getScenarioId(), orbat.getScenarioId()))
                .orElse(false);
    }

    private StandardOrbat standardOrbat(Team team) {
        List<StandardOrbat> found = standardOrbats.findByTeamId(team.getId());
        if (found.isEmpty()) {
            throw new BadRequestException("Team " + team.getName() + " has no standard ORBAT.");
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

    private PlayingOrbat find(Long id) {
        return playingOrbats.findById(id).orElseThrow(() -> new NotFoundException("Playing ORBAT not found."));
    }

    private void requireScenario(Long scenarioId) {
        if (!scenarios.existsById(scenarioId)) {
            throw new NotFoundException("Scenario not found.");
        }
    }

    public record Alliance(Team team, List<Long> memberTeamIds, PlayingOrbat playingOrbat) {
    }
}
