package com.drdo.jayant.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drdo.jayant.domain.Team;
import com.drdo.jayant.repository.AllianceMemberRepository;
import com.drdo.jayant.repository.AppUserRepository;
import com.drdo.jayant.repository.PlayingOrbatRepository;
import com.drdo.jayant.repository.StandardOrbatRepository;
import com.drdo.jayant.repository.TeamRepository;
import com.drdo.jayant.web.ConflictException;
import com.drdo.jayant.web.ForbiddenException;
import com.drdo.jayant.web.NotFoundException;

@Service
public class TeamService {

    public static final String KIND_COUNTRY = "country";
    public static final String KIND_ALLIANCE = "alliance";

    private final TeamRepository teams;
    private final StandardOrbatRepository standardOrbats;
    private final PlayingOrbatRepository playingOrbats;
    private final AllianceMemberRepository allianceMembers;
    private final AppUserRepository users;
    private final ThemeService themes;
    private final IconService icons;
    private final AccessService access;
    private final AuditService audit;

    public TeamService(TeamRepository teams, StandardOrbatRepository standardOrbats,
            PlayingOrbatRepository playingOrbats, AllianceMemberRepository allianceMembers, AppUserRepository users,
            ThemeService themes, IconService icons, AccessService access, AuditService audit) {
        this.teams = teams;
        this.standardOrbats = standardOrbats;
        this.playingOrbats = playingOrbats;
        this.allianceMembers = allianceMembers;
        this.users = users;
        this.themes = themes;
        this.icons = icons;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Team> list() {
        return teams.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Team get(Long id) {
        return teams.findById(id).orElseThrow(() -> new NotFoundException("Team not found."));
    }

    /** Creates a country team; alliances are created through the scenario alliance endpoint. */
    @Transactional
    public Team create(String name, String description, String colour, String icon, Long themeId) {
        if (access.isTeamScoped()) {
            throw new ForbiddenException("A team administrator cannot create teams.");
        }
        Team team = build(new Team(), KIND_COUNTRY, name, description, colour, icon, themeId);
        Team saved = teams.save(team);
        audit.record("CREATE", "Team", saved.getId(), "Team " + saved.getName() + " created", Map.of());
        return saved;
    }

    @Transactional
    public Team update(Long id, String name, String description, String colour, String icon, Long themeId) {
        Team team = get(id);
        access.requireTeamScope(id);
        Team saved = teams.save(build(team, team.getKind(), name, description, colour, icon, themeId));
        audit.record("UPDATE", "Team", saved.getId(), "Team " + saved.getName() + " updated", Map.of());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Team team = get(id);
        if (access.isTeamScoped()) {
            throw new ForbiddenException("A team administrator cannot delete teams.");
        }
        if (!standardOrbats.findByTeamId(id).isEmpty() || !playingOrbats.findByTeamId(id).isEmpty()
                || !allianceMembers.findByMemberTeamId(id).isEmpty() || !users.findByAdministeredTeamId(id).isEmpty()) {
            throw new ConflictException("The team is in use by ORBATs, alliances or users.");
        }
        allianceMembers.deleteAll(allianceMembers.findByAllianceTeamId(id));
        teams.delete(team);
        audit.record("DELETE", "Team", id, "Team " + team.getName() + " deleted", Map.of());
    }

    /** Fills the editable fields; shared with alliance creation. */
    Team build(Team team, String kind, String name, String description, String colour, String icon, Long themeId) {
        String trimmed = name.trim();
        teams.findByName(trimmed).filter(other -> !other.getId().equals(team.getId())).ifPresent(other -> {
            throw new ConflictException("A team with this name already exists.");
        });
        themes.requireExists(themeId);
        if (icon != null && !icon.isBlank()) {
            icons.read(icon);
        }
        team.setName(trimmed);
        team.setKind(kind);
        team.setDescription(description);
        team.setColour(colour == null || colour.isBlank() ? null : colour.trim());
        team.setIcon(icon == null || icon.isBlank() ? null : icon.trim());
        team.setThemeId(themeId);
        return team;
    }
}
