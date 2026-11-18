package com.drdo.jayant.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.drdo.jayant.domain.AppUser;
import com.drdo.jayant.domain.FunctionalityCodes;
import com.drdo.jayant.domain.Functionality;
import com.drdo.jayant.domain.RoleCodes;
import com.drdo.jayant.domain.RoleEntity;
import com.drdo.jayant.domain.Theme;
import com.drdo.jayant.repository.AppUserRepository;
import com.drdo.jayant.repository.FunctionalityRepository;
import com.drdo.jayant.repository.RoleRepository;
import com.drdo.jayant.repository.ThemeRepository;
import com.drdo.jayant.service.IconService;

/**
 * Idempotent seed of the default theme, functionalities, roles, the APEX administrator and starter icons.
 * Existing rows are never overwritten, except that the ADMINISTRATOR role always receives every functionality.
 */
@Component
@Order(10)
public class DomainSeedRunner implements ApplicationRunner {

    static final String DEFAULT_THEME = "Default";
    static final String APEX_USER = "APEX";
    static final String APEX_PASSWORD = "123";

    private static final Logger log = LogManager.getLogger(DomainSeedRunner.class);

    private static final Map<String, String> FUNCTIONALITY_NAMES = Map.ofEntries(
            Map.entry(FunctionalityCodes.MAP_VIEW, "Map view"),
            Map.entry(FunctionalityCodes.MANAGE_THEMES, "Manage themes"),
            Map.entry(FunctionalityCodes.MANAGE_SIDES, "Manage sides"),
            Map.entry(FunctionalityCodes.MANAGE_NODE_TYPES, "Manage node types"),
            Map.entry(FunctionalityCodes.MANAGE_ROLES, "Manage roles"),
            Map.entry(FunctionalityCodes.MANAGE_USERS, "Manage users"),
            Map.entry(FunctionalityCodes.MARK_AS_USER, "Mark as user"),
            Map.entry(FunctionalityCodes.MANAGE_STANDARD_ORBAT, "Manage standard ORBAT"),
            Map.entry(FunctionalityCodes.MANAGE_PLAYING_ORBAT, "Manage playing ORBAT"),
            Map.entry(FunctionalityCodes.MANAGE_ALLIANCE, "Manage alliance"),
            Map.entry(FunctionalityCodes.MANAGE_SCENARIO, "Manage scenario"),
            Map.entry(FunctionalityCodes.MANAGE_GAME, "Manage game"),
            Map.entry(FunctionalityCodes.MANAGE_ICONS, "Manage icons"),
            Map.entry(FunctionalityCodes.VIEW_AUDIT, "View audit"),
            Map.entry(FunctionalityCodes.CHOOSE_THEME, "Choose theme"));

    private final ThemeRepository themes;
    private final FunctionalityRepository functionalities;
    private final RoleRepository roles;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final IconService icons;
    private final TransactionTemplate transaction;

    public DomainSeedRunner(ThemeRepository themes, FunctionalityRepository functionalities, RoleRepository roles,
            AppUserRepository users, PasswordEncoder passwordEncoder, IconService icons,
            TransactionTemplate transaction) {
        this.themes = themes;
        this.functionalities = functionalities;
        this.roles = roles;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.icons = icons;
        this.transaction = transaction;
    }

    @Override
    public void run(ApplicationArguments args) {
        transaction.executeWithoutResult(status -> seed());
        icons.seedDefaults();
        log.info("Domain seed complete");
    }

    private void seed() {
        seedTheme();
        List<Long> allFunctionalityIds = seedFunctionalities();
        Map<String, Long> functionalityIds = functionalityIdsByCode();
        RoleEntity administrator = seedRole(RoleCodes.ADMINISTRATOR, "Administrator", allFunctionalityIds, true);
        seedRole(RoleCodes.SIDE_ADMINISTRATOR, "Side administrator", ids(functionalityIds,
                FunctionalityCodes.MAP_VIEW, FunctionalityCodes.MANAGE_SIDES, FunctionalityCodes.MANAGE_USERS,
                FunctionalityCodes.MARK_AS_USER, FunctionalityCodes.MANAGE_STANDARD_ORBAT, FunctionalityCodes.CHOOSE_THEME), false);
        seedRole(RoleCodes.COMMANDER, "Commander",
                ids(functionalityIds, FunctionalityCodes.MAP_VIEW, FunctionalityCodes.CHOOSE_THEME), false);
        seedRole(RoleCodes.PLANNER, "Planner",
                ids(functionalityIds, FunctionalityCodes.MAP_VIEW, FunctionalityCodes.CHOOSE_THEME), false);
        seedApex(administrator);
    }

    private void seedTheme() {
        if (themes.findFirstByDefaultThemeTrueOrderByIdAsc().isPresent() || themes.findByName(DEFAULT_THEME).isPresent()) {
            return;
        }
        Theme theme = new Theme();
        theme.setName(DEFAULT_THEME);
        theme.setDescription("Default Jayant theme");
        theme.setDefaultTheme(true);
        theme.setConfigJson(Map.of("mode", "light"));
        themes.save(theme);
    }

    private List<Long> seedFunctionalities() {
        List<Long> ids = new ArrayList<>();
        for (String code : FunctionalityCodes.ALL) {
            Functionality functionality = functionalities.findByCode(code).orElseGet(() -> {
                Functionality created = new Functionality();
                created.setCode(code);
                created.setName(FUNCTIONALITY_NAMES.getOrDefault(code, code));
                return functionalities.save(created);
            });
            ids.add(functionality.getId());
        }
        return ids;
    }

    private Map<String, Long> functionalityIdsByCode() {
        Map<String, Long> result = new java.util.HashMap<>();
        functionalities.findAll().forEach(functionality -> result.put(functionality.getCode(), functionality.getId()));
        return result;
    }

    private static List<Long> ids(Map<String, Long> byCode, String... codes) {
        List<Long> ids = new ArrayList<>();
        for (String code : codes) {
            ids.add(byCode.get(code));
        }
        return ids;
    }

    private RoleEntity seedRole(String code, String name, List<Long> functionalityIds, boolean refresh) {
        RoleEntity role = roles.findByCode(code).orElse(null);
        if (role == null) {
            role = new RoleEntity();
            role.setCode(code);
            role.setName(name);
            role.setFunctionalityIds(new ArrayList<>(functionalityIds));
            return roles.save(role);
        }
        if (refresh && !functionalityIds.equals(role.getFunctionalityIds())) {
            role.setFunctionalityIds(new ArrayList<>(functionalityIds));
            return roles.save(role);
        }
        return role;
    }

    private void seedApex(RoleEntity administrator) {
        if (users.findByName(APEX_USER).isPresent()) {
            return;
        }
        AppUser apex = new AppUser();
        apex.setName(APEX_USER);
        apex.setPasswordHash(passwordEncoder.encode(APEX_PASSWORD));
        apex.setActive(true);
        apex.setRoleIds(new ArrayList<>(List.of(administrator.getId())));
        users.save(apex);
    }
}
