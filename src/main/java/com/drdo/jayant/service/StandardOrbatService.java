package com.drdo.jayant.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drdo.jayant.domain.FeatureCodes;
import com.drdo.jayant.domain.StandardOrbat;
import com.drdo.jayant.domain.Team;
import com.drdo.jayant.repository.StandardOrbatRepository;
import com.drdo.jayant.repository.TeamRepository;
import com.drdo.jayant.web.BadRequestException;
import com.drdo.jayant.web.ConflictException;
import com.drdo.jayant.web.NotFoundException;

@Service
public class StandardOrbatService {

    private final StandardOrbatRepository orbats;
    private final TeamRepository teams;
    private final OrbatSupport support;
    private final AccessService access;
    private final AuditService audit;

    public StandardOrbatService(StandardOrbatRepository orbats, TeamRepository teams, OrbatSupport support,
            AccessService access, AuditService audit) {
        this.orbats = orbats;
        this.teams = teams;
        this.support = support;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public StandardOrbat get(Long teamId) {
        access.requireAnyFeature(FeatureCodes.MANAGE_STANDARD_ORBAT, FeatureCodes.MARK_AS_USER,
                FeatureCodes.MANAGE_PLAYING_ORBAT, FeatureCodes.MANAGE_ALLIANCE, FeatureCodes.MANAGE_USERS);
        team(teamId);
        access.requireTeamScope(teamId);
        return find(teamId);
    }

    @Transactional
    public Created create(Long teamId) {
        access.requireFeature(FeatureCodes.MANAGE_STANDARD_ORBAT);
        Team team = team(teamId);
        access.requireTeamScope(teamId);
        List<StandardOrbat> existing = orbats.findByTeamId(teamId);
        if (!existing.isEmpty()) {
            return new Created(existing.get(0), false);
        }
        if (!TeamService.KIND_COUNTRY.equals(team.getKind())) {
            throw new BadRequestException("Only country teams have a standard ORBAT.");
        }
        StandardOrbat orbat = new StandardOrbat();
        orbat.setName(team.getName());
        orbat.setTeamId(teamId);
        orbat.setOrbatJson(OrbatSupport.document(List.of(OrbatSupport.rootNode(team.getName(), teamId))));
        orbat.setMetadata(new java.util.LinkedHashMap<>());
        StandardOrbat saved = orbats.saveAndFlush(orbat);
        audit.record("CREATE", "StandardOrbat", saved.getId(), "Standard ORBAT created for " + team.getName(),
                Map.of("teamId", teamId));
        return new Created(saved, true);
    }

    @Transactional
    public StandardOrbat update(Long teamId, Map<String, Object> orbatJson, Map<String, Object> metadata, Long version) {
        access.requireFeature(FeatureCodes.MANAGE_STANDARD_ORBAT);
        team(teamId);
        access.requireTeamScope(teamId);
        StandardOrbat orbat = find(teamId);
        if (!Objects.equals(orbat.getVersion(), version)) {
            throw new ConflictException("The standard ORBAT was changed by someone else. Reload and try again.");
        }
        int nodes = support.validate(orbatJson).size();
        orbat.setOrbatJson(orbatJson);
        if (metadata != null) {
            orbat.setMetadata(metadata);
        }
        StandardOrbat saved = orbats.saveAndFlush(orbat);
        audit.record("UPDATE", "StandardOrbat", saved.getId(), "Standard ORBAT saved for team " + teamId,
                Map.of("teamId", teamId, "nodes", nodes, "version", saved.getVersion()));
        return saved;
    }

    private Team team(Long teamId) {
        return teams.findById(teamId).orElseThrow(() -> new NotFoundException("Team not found."));
    }

    private StandardOrbat find(Long teamId) {
        List<StandardOrbat> found = orbats.findByTeamId(teamId);
        if (found.isEmpty()) {
            throw new NotFoundException("The team has no standard ORBAT yet.");
        }
        return found.get(0);
    }

    public record Created(StandardOrbat orbat, boolean created) {
    }
}
