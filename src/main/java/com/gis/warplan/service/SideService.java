package com.gis.warplan.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.Side;
import com.gis.warplan.repository.AllianceMemberRepository;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.PlayingOrbatRepository;
import com.gis.warplan.repository.StandardOrbatRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.ForbiddenException;
import com.gis.warplan.web.NotFoundException;

@Service
public class SideService {

    private static final Logger log = LogManager.getLogger(SideService.class);

    public static final String KIND_COUNTRY = "country";
    public static final String KIND_ALLIANCE = "alliance";

    private final SideRepository sides;
    private final StandardOrbatRepository standardOrbats;
    private final PlayingOrbatRepository playingOrbats;
    private final AllianceMemberRepository allianceMembers;
    private final AppUserRepository users;
    private final ThemeService themes;
    private final IconService icons;
    private final AccessService access;
    private final AuditService audit;

    public SideService(SideRepository sides, StandardOrbatRepository standardOrbats,
            PlayingOrbatRepository playingOrbats, AllianceMemberRepository allianceMembers, AppUserRepository users,
            ThemeService themes, IconService icons, AccessService access, AuditService audit) {
        this.sides = sides;
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
    public List<Side> list() {
        return sides.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Side get(Long id) {
        return sides.findById(id).orElseThrow(() -> new NotFoundException("Side not found."));
    }

    /** Creates a country side; alliances are created through the game alliance endpoint. */
    @Transactional
    public Side create(String name, String description, String colour, String icon, Long themeId) {
        if (access.isSideScoped()) {
            throw new ForbiddenException("A side administrator cannot create sides.");
        }
        Side side = build(new Side(), KIND_COUNTRY, name, description, colour, icon, themeId);
        Side saved = sides.save(side);
        log.info("side.created id={} name={}", saved.getId(), saved.getName());
        audit.record("CREATE", "Side", saved.getId(), "Side " + saved.getName() + " created", Map.of());
        return saved;
    }

    @Transactional
    public Side update(Long id, String name, String description, String colour, String icon, Long themeId) {
        Side side = get(id);
        access.requireSideScope(id);
        Side saved = sides.save(build(side, side.getKind(), name, description, colour, icon, themeId));
        log.info("side.updated id={}", saved.getId());
        audit.record("UPDATE", "Side", saved.getId(), "Side " + saved.getName() + " updated", Map.of());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Side side = get(id);
        if (access.isSideScoped()) {
            throw new ForbiddenException("A side administrator cannot delete sides.");
        }
        if (!standardOrbats.findBySideId(id).isEmpty() || !playingOrbats.findBySideId(id).isEmpty()
                || !allianceMembers.findByMemberSideId(id).isEmpty() || !users.findByAdministeredSideId(id).isEmpty()) {
            throw new ConflictException("The side is in use by ORBATs, alliances or users.");
        }
        allianceMembers.deleteAll(allianceMembers.findByAllianceSideId(id));
        sides.delete(side);
        log.info("side.deleted id={}", id);
        audit.record("DELETE", "Side", id, "Side " + side.getName() + " deleted", Map.of());
    }

    /**
     * Fills the editable fields; shared with alliance creation. Names are unique among country sides and, for
     * alliance sides, within their game (set {@code gameId} on the side before calling).
     */
    Side build(Side side, String kind, String name, String description, String colour, String icon, Long themeId) {
        String trimmed = name.trim();
        sides.findByName(trimmed).stream()
                .filter(other -> !other.getId().equals(side.getId()))
                .filter(other -> Objects.equals(other.getGameId(), side.getGameId()))
                .findAny()
                .ifPresent(other -> {
                    throw new ConflictException("A side with this name already exists.");
                });
        themes.requireExists(themeId);
        if (icon != null && !icon.isBlank()) {
            icons.read(icon);
        }
        side.setName(trimmed);
        side.setKind(kind);
        side.setDescription(description);
        side.setColour(colour == null || colour.isBlank() ? null : colour.trim());
        side.setIcon(icon == null || icon.isBlank() ? null : icon.trim());
        side.setThemeId(themeId);
        return side;
    }
}
