package com.gis.warplan.service;

import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.Side;
import com.gis.warplan.domain.StandardOrbat;
import com.gis.warplan.domain.Theme;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.repository.StandardOrbatRepository;
import com.gis.warplan.repository.ThemeRepository;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.ConflictException;

/**
 * A theme record has one holder: one side, one user, or nobody. APEX's theme is the login theme and is
 * never offered to a side or to another user. Side members display the side theme without a theme of their own.
 */
@Service
public class ThemeAllotmentService {

    public static final String APEX_NAME = "APEX";
    public static final String HOLDER_FREE = "FREE";
    public static final String HOLDER_SIDE = "SIDE";
    public static final String HOLDER_USER = "USER";

    private final ThemeRepository themes;
    private final SideRepository sides;
    private final AppUserRepository users;
    private final StandardOrbatRepository standardOrbats;

    public ThemeAllotmentService(ThemeRepository themes, SideRepository sides, AppUserRepository users,
            StandardOrbatRepository standardOrbats) {
        this.themes = themes;
        this.sides = sides;
        this.users = users;
        this.standardOrbats = standardOrbats;
    }

    public boolean isApex(AppUser user) {
        return user != null && APEX_NAME.equals(user.getName());
    }

    /** Country side of a marked node, otherwise the side the account administers. */
    @Transactional(readOnly = true)
    public Long homeSideId(AppUser user) {
        if (user.getStandardOrbatId() != null) {
            Long fromNode = standardOrbats.findById(user.getStandardOrbatId()).map(StandardOrbat::getSideId).orElse(null);
            if (fromNode != null) {
                return fromNode;
            }
        }
        return user.getAdministeredSideId();
    }

    /** Own allotment when set, otherwise the home side's theme. */
    @Transactional(readOnly = true)
    public Long displayThemeId(AppUser user) {
        if (user.getThemeId() != null && themes.existsById(user.getThemeId())) {
            return user.getThemeId();
        }
        Long sideId = homeSideId(user);
        if (sideId == null) {
            return null;
        }
        return sides.findById(sideId).map(Side::getThemeId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Theme loginTheme() {
        AppUser apex = users.findByName(APEX_NAME).orElse(null);
        if (apex == null || apex.getThemeId() == null) {
            return null;
        }
        return themes.findById(apex.getThemeId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public void requireAllottableToSide(Long themeId, Long sideId) {
        Theme theme = requireTheme(themeId, "A side requires a theme.");
        for (AppUser user : users.findByThemeId(theme.getId())) {
            String who = isApex(user) ? "APEX, and that theme is the login theme" : user.getName();
            throw new ConflictException("That theme is allotted to " + who + ".");
        }
        for (Side side : sides.findByThemeId(theme.getId())) {
            if (sideId != null && sideId.equals(side.getId())) {
                continue;
            }
            throw new ConflictException("That theme is allotted to " + side.getName() + ".");
        }
    }

    @Transactional(readOnly = true)
    public void requireAllottableToUser(Long themeId, Long userId) {
        Theme theme = requireTheme(themeId, "Choose a theme.");
        for (Side side : sides.findByThemeId(theme.getId())) {
            throw new ConflictException("That theme is allotted to " + side.getName() + ".");
        }
        for (AppUser user : users.findByThemeId(theme.getId())) {
            if (userId != null && userId.equals(user.getId())) {
                continue;
            }
            String who = isApex(user) ? "APEX, and that theme is the login theme" : user.getName();
            throw new ConflictException("That theme is allotted to " + who + ".");
        }
    }

    /** Drops a user's override so the account inherits the home side. APEX and unsided accounts keep a theme. */
    public void clearOverride(AppUser user) {
        if (isApex(user)) {
            throw new ConflictException("APEX keeps a personal theme. It is the login theme.");
        }
        if (homeSideId(user) == null) {
            throw new BadRequestException("A user without a side needs a theme.");
        }
        user.setThemeId(null);
    }

    @Transactional(readOnly = true)
    public Holder holderOf(Long themeId) {
        List<AppUser> holders = users.findByThemeId(themeId);
        if (!holders.isEmpty()) {
            AppUser user = holders.get(0);
            return new Holder(HOLDER_USER, user.getId(), user.getName(), isApex(user));
        }
        List<Side> sideHolders = sides.findByThemeId(themeId);
        if (!sideHolders.isEmpty()) {
            Side side = sideHolders.get(0);
            return new Holder(HOLDER_SIDE, side.getId(), side.getName(), false);
        }
        return new Holder(HOLDER_FREE, null, "", false);
    }

    private Theme requireTheme(Long themeId, String missingMessage) {
        if (themeId == null) {
            throw new BadRequestException(missingMessage);
        }
        return themes.findById(themeId).orElseThrow(() -> new BadRequestException("Theme " + themeId + " does not exist."));
    }

    public record Holder(String kind, Long id, String name, boolean loginTheme) {
    }

    /** Copies a theme's colours under a new name. The copy is free until a caller stores its id. */
    @Transactional
    public Theme cloneTheme(Theme source, String baseName) {
        Theme copy = new Theme();
        copy.setName(uniqueName(baseName));
        copy.setDescription(source.getDescription());
        copy.setDefaultTheme(false);
        if (source.getConfigJson() != null) {
            copy.setConfigJson(new LinkedHashMap<>(source.getConfigJson()));
        }
        return themes.save(copy);
    }

    private String uniqueName(String base) {
        String trimmed = base == null || base.isBlank() ? "Theme" : base.trim();
        if (trimmed.length() > 110) {
            trimmed = trimmed.substring(0, 110).trim();
        }
        String candidate = trimmed;
        int suffix = 2;
        while (themes.findByName(candidate).isPresent()) {
            candidate = trimmed + " " + suffix;
            suffix++;
        }
        return candidate;
    }
}
