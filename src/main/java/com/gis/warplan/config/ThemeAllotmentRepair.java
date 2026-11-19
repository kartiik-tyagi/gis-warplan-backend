package com.gis.warplan.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.Side;
import com.gis.warplan.domain.Theme;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.repository.ThemeRepository;
import com.gis.warplan.service.SideService;
import com.gis.warplan.service.StandardOrbatService;
import com.gis.warplan.service.ThemeAllotmentService;

/**
 * Makes existing rows obey exclusive theme allotment: APEX holds the login theme, every side holds its own
 * theme, and every country side has a standard-ORBAT root. Fresh databases only allot the seeded login theme.
 */
@Component
@Order(20)
public class ThemeAllotmentRepair implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(ThemeAllotmentRepair.class);

    private final ThemeRepository themes;
    private final SideRepository sides;
    private final AppUserRepository users;
    private final ThemeAllotmentService allotment;
    private final StandardOrbatService standardOrbats;
    private final TransactionTemplate transaction;

    public ThemeAllotmentRepair(ThemeRepository themes, SideRepository sides, AppUserRepository users,
            ThemeAllotmentService allotment, StandardOrbatService standardOrbats, TransactionTemplate transaction) {
        this.themes = themes;
        this.sides = sides;
        this.users = users;
        this.allotment = allotment;
        this.standardOrbats = standardOrbats;
        this.transaction = transaction;
    }

    @Override
    public void run(ApplicationArguments args) {
        transaction.executeWithoutResult(status -> repair());
    }

    private void repair() {
        AppUser apex = users.findByName(ThemeAllotmentService.APEX_NAME).orElse(null);
        Theme login = themes.findFirstByDefaultThemeTrueOrderByIdAsc().orElse(null);
        if (apex != null && apex.getThemeId() == null && login != null) {
            moveHoldersOff(login, apex.getId());
            apex.setThemeId(login.getId());
            users.save(apex);
            log.info("theme.repair apex loginThemeId={}", login.getId());
        }
        for (Theme theme : new ArrayList<>(themes.findAll())) {
            separateHolders(theme.getId());
        }
        Theme palette = login != null ? login : themes.findAll().stream().findFirst().orElse(null);
        if (palette != null) {
            for (Side side : sides.findAll()) {
                if (side.getThemeId() == null) {
                    side.setThemeId(allotment.cloneTheme(palette, side.getName() + " theme").getId());
                    sides.save(side);
                    log.info("theme.repair side id={} themeId={}", side.getId(), side.getThemeId());
                }
            }
            for (AppUser user : users.findAll()) {
                if (user.getThemeId() != null || allotment.homeSideId(user) != null) {
                    continue;
                }
                user.setThemeId(allotment.cloneTheme(palette, user.getName() + " theme").getId());
                users.save(user);
                log.info("theme.repair user id={} themeId={}", user.getId(), user.getThemeId());
            }
        }
        for (Side side : sides.findByKind(SideService.KIND_COUNTRY)) {
            standardOrbats.createRootIfAbsent(side);
        }
    }

    /** Leaves the theme with at most one holder. A side is kept in preference to its own members, who then inherit. */
    private void separateHolders(Long themeId) {
        List<Side> sideHolders = new ArrayList<>(sides.findByThemeId(themeId));
        List<AppUser> userHolders = new ArrayList<>(users.findByThemeId(themeId));
        if (sideHolders.size() + userHolders.size() <= 1) {
            return;
        }
        Theme source = themes.findById(themeId).orElse(null);
        if (source == null) {
            return;
        }
        AppUser apex = userHolders.stream().filter(allotment::isApex).findFirst().orElse(null);
        if (apex != null) {
            sideHolders.forEach(side -> moveSide(side, source));
            userHolders.stream().filter(user -> !user.getId().equals(apex.getId())).forEach(user -> moveUser(user, source));
            return;
        }
        if (!sideHolders.isEmpty()) {
            Side keep = sideHolders.get(0);
            for (Side side : sideHolders) {
                if (!side.getId().equals(keep.getId())) {
                    moveSide(side, source);
                }
            }
            for (AppUser user : userHolders) {
                if (keep.getId().equals(allotment.homeSideId(user))) {
                    user.setThemeId(null);
                    users.save(user);
                } else {
                    moveUser(user, source);
                }
            }
            return;
        }
        AppUser keep = userHolders.get(0);
        for (AppUser user : userHolders) {
            if (!user.getId().equals(keep.getId())) {
                moveUser(user, source);
            }
        }
    }

    private void moveHoldersOff(Theme theme, Long exceptUserId) {
        for (Side side : new ArrayList<>(sides.findByThemeId(theme.getId()))) {
            moveSide(side, theme);
        }
        for (AppUser user : new ArrayList<>(users.findByThemeId(theme.getId()))) {
            if (exceptUserId != null && exceptUserId.equals(user.getId())) {
                continue;
            }
            moveUser(user, theme);
        }
    }

    private void moveSide(Side side, Theme source) {
        side.setThemeId(allotment.cloneTheme(source, side.getName() + " theme").getId());
        sides.save(side);
    }

    private void moveUser(AppUser user, Theme source) {
        user.setThemeId(allotment.cloneTheme(source, user.getName() + " theme").getId());
        users.save(user);
    }
}
