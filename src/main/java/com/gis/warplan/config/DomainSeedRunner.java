package com.gis.warplan.config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.Functionality;
import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.RoleCodes;
import com.gis.warplan.domain.RoleEntity;
import com.gis.warplan.domain.Theme;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.FunctionalityRepository;
import com.gis.warplan.repository.RoleRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.repository.ThemeRepository;
import com.gis.warplan.service.IconService;
import com.gis.warplan.service.MapCatalogSeedService;

/**
 * Idempotent seed of themes, functionalities, roles, APEX, map catalog and starter icons.
 * Existing theme rows are never overwritten so user edits to configJson stay intact.
 * The ADMINISTRATOR role always receives every functionality.
 */
@Component
@Order(10)
public class DomainSeedRunner implements ApplicationRunner {

    static final String LEGACY_DEFAULT_THEME = "Default";
    static final String PINE_NIGHT = "Pine, night";
    static final String APEX_USER = "APEX";
    static final String APEX_PASSWORD = "123";

    private static final Logger log = LogManager.getLogger(DomainSeedRunner.class);

    private static final Map<String, String> FUNCTIONALITY_NAMES = Map.ofEntries(
            Map.entry(FunctionalityCodes.MAP_VIEW, "Map view"),
            Map.entry(FunctionalityCodes.MANAGE_MAP, "Manage map"),
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
            Map.entry(FunctionalityCodes.MANAGE_ICONS, "Manage assets"),
            Map.entry(FunctionalityCodes.VIEW_AUDIT, "View audit"),
            Map.entry(FunctionalityCodes.CHOOSE_THEME, "Choose theme"));

    private final ThemeRepository themes;
    private final FunctionalityRepository functionalities;
    private final RoleRepository roles;
    private final AppUserRepository users;
    private final SideRepository sides;
    private final PasswordEncoder passwordEncoder;
    private final IconService icons;
    private final MapCatalogSeedService mapCatalogSeed;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public DomainSeedRunner(ThemeRepository themes, FunctionalityRepository functionalities, RoleRepository roles,
            AppUserRepository users, SideRepository sides, PasswordEncoder passwordEncoder, IconService icons,
            MapCatalogSeedService mapCatalogSeed, ObjectMapper objectMapper, TransactionTemplate transaction) {
        this.themes = themes;
        this.functionalities = functionalities;
        this.roles = roles;
        this.users = users;
        this.sides = sides;
        this.passwordEncoder = passwordEncoder;
        this.icons = icons;
        this.mapCatalogSeed = mapCatalogSeed;
        this.objectMapper = objectMapper;
        this.transaction = transaction;
    }

    @Override
    public void run(ApplicationArguments args) {
        transaction.executeWithoutResult(status -> seed());
        icons.seedDefaults();
        log.info("Domain seed complete");
    }

    private void seed() {
        seedThemes();
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
        mapCatalogSeed.seedIfEmpty();
    }

    private void seedThemes() {
        List<ThemeSeed> seeds = loadThemeSeeds();
        for (ThemeSeed seed : seeds) {
            if (themes.findByName(seed.name()).isPresent()) {
                continue;
            }
            Theme theme = new Theme();
            theme.setName(seed.name());
            theme.setDescription(seed.description());
            theme.setDefaultTheme(false);
            theme.setConfigJson(seed.configJson());
            themes.save(theme);
            log.info("Seeded theme {}", seed.name());
        }

        if (themes.findFirstByDefaultThemeTrueOrderByIdAsc().isEmpty()) {
            themes.findByName(PINE_NIGHT).ifPresent(pine -> {
                pine.setDefaultTheme(true);
                themes.save(pine);
                log.info("Marked {} as default theme", PINE_NIGHT);
            });
        }

        themes.findByName(LEGACY_DEFAULT_THEME).ifPresent(legacy -> {
            if (legacy.isDefaultTheme()) {
                return;
            }
            Long id = legacy.getId();
            if (users.countByThemeId(id) == 0 && sides.countByThemeId(id) == 0) {
                themes.delete(legacy);
                log.info("Removed unused legacy theme {}", LEGACY_DEFAULT_THEME);
            }
        });
    }

    private List<ThemeSeed> loadThemeSeeds() {
        try (InputStream in = new ClassPathResource("seed/themes.json").getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<ThemeSeed>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load seed/themes.json", ex);
        }
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
            String expected = FUNCTIONALITY_NAMES.get(code);
            if (expected != null && !expected.equals(functionality.getName())) {
                functionality.setName(expected);
                functionality = functionalities.save(functionality);
            }
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
            role.setStandard(true);
            role.setFunctionalityIds(new ArrayList<>(functionalityIds));
            return roles.save(role);
        }
        boolean changed = false;
        if (!role.isStandard()) {
            role.setStandard(true);
            changed = true;
        }
        if (refresh && !functionalityIds.equals(role.getFunctionalityIds())) {
            role.setFunctionalityIds(new ArrayList<>(functionalityIds));
            changed = true;
        }
        return changed ? roles.save(role) : role;
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

    public record ThemeSeed(String name, String description, boolean defaultTheme, Map<String, Object> configJson) {
    }
}
