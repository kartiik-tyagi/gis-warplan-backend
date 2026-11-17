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
import com.drdo.jayant.domain.FeatureCodes;
import com.drdo.jayant.domain.FeatureEntity;
import com.drdo.jayant.domain.RoleCodes;
import com.drdo.jayant.domain.RoleEntity;
import com.drdo.jayant.domain.Theme;
import com.drdo.jayant.repository.AppUserRepository;
import com.drdo.jayant.repository.FeatureRepository;
import com.drdo.jayant.repository.RoleRepository;
import com.drdo.jayant.repository.ThemeRepository;
import com.drdo.jayant.service.IconService;

/**
 * Idempotent seed of the default theme, features, roles, the APEX administrator and starter icons.
 * Existing rows are never overwritten, except that the ADMINISTRATOR role always receives every feature.
 */
@Component
@Order(10)
public class DomainSeedRunner implements ApplicationRunner {

    static final String DEFAULT_THEME = "Default";
    static final String APEX_USER = "APEX";
    static final String APEX_PASSWORD = "123";

    private static final Logger log = LogManager.getLogger(DomainSeedRunner.class);

    private static final Map<String, String> FEATURE_NAMES = Map.ofEntries(
            Map.entry(FeatureCodes.MAP_VIEW, "Map view"),
            Map.entry(FeatureCodes.MANAGE_THEMES, "Manage themes"),
            Map.entry(FeatureCodes.MANAGE_TEAMS, "Manage teams"),
            Map.entry(FeatureCodes.MANAGE_NODE_TYPES, "Manage node types"),
            Map.entry(FeatureCodes.MANAGE_ROLES, "Manage roles"),
            Map.entry(FeatureCodes.MANAGE_USERS, "Manage users"),
            Map.entry(FeatureCodes.MARK_AS_USER, "Mark as user"),
            Map.entry(FeatureCodes.MANAGE_STANDARD_ORBAT, "Manage standard ORBAT"),
            Map.entry(FeatureCodes.MANAGE_PLAYING_ORBAT, "Manage playing ORBAT"),
            Map.entry(FeatureCodes.MANAGE_ALLIANCE, "Manage alliance"),
            Map.entry(FeatureCodes.MANAGE_SCENARIO, "Manage scenario"),
            Map.entry(FeatureCodes.MANAGE_GAME, "Manage game"),
            Map.entry(FeatureCodes.MANAGE_ICONS, "Manage icons"),
            Map.entry(FeatureCodes.VIEW_AUDIT, "View audit"),
            Map.entry(FeatureCodes.CHOOSE_THEME, "Choose theme"));

    private final ThemeRepository themes;
    private final FeatureRepository features;
    private final RoleRepository roles;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final IconService icons;
    private final TransactionTemplate transaction;

    public DomainSeedRunner(ThemeRepository themes, FeatureRepository features, RoleRepository roles,
            AppUserRepository users, PasswordEncoder passwordEncoder, IconService icons,
            TransactionTemplate transaction) {
        this.themes = themes;
        this.features = features;
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
        List<Long> allFeatureIds = seedFeatures();
        Map<String, Long> featureIds = featureIdsByCode();
        RoleEntity administrator = seedRole(RoleCodes.ADMINISTRATOR, "Administrator", allFeatureIds, true);
        seedRole(RoleCodes.TEAM_ADMINISTRATOR, "Team administrator", ids(featureIds,
                FeatureCodes.MAP_VIEW, FeatureCodes.MANAGE_TEAMS, FeatureCodes.MANAGE_USERS,
                FeatureCodes.MARK_AS_USER, FeatureCodes.MANAGE_STANDARD_ORBAT, FeatureCodes.CHOOSE_THEME), false);
        seedRole(RoleCodes.COMMANDER, "Commander",
                ids(featureIds, FeatureCodes.MAP_VIEW, FeatureCodes.CHOOSE_THEME), false);
        seedRole(RoleCodes.PLANNER, "Planner",
                ids(featureIds, FeatureCodes.MAP_VIEW, FeatureCodes.CHOOSE_THEME), false);
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

    private List<Long> seedFeatures() {
        List<Long> ids = new ArrayList<>();
        for (String code : FeatureCodes.ALL) {
            FeatureEntity feature = features.findByCode(code).orElseGet(() -> {
                FeatureEntity created = new FeatureEntity();
                created.setCode(code);
                created.setName(FEATURE_NAMES.getOrDefault(code, code));
                return features.save(created);
            });
            ids.add(feature.getId());
        }
        return ids;
    }

    private Map<String, Long> featureIdsByCode() {
        Map<String, Long> result = new java.util.HashMap<>();
        features.findAll().forEach(feature -> result.put(feature.getCode(), feature.getId()));
        return result;
    }

    private static List<Long> ids(Map<String, Long> byCode, String... codes) {
        List<Long> ids = new ArrayList<>();
        for (String code : codes) {
            ids.add(byCode.get(code));
        }
        return ids;
    }

    private RoleEntity seedRole(String code, String name, List<Long> featureIds, boolean refresh) {
        RoleEntity role = roles.findByCode(code).orElse(null);
        if (role == null) {
            role = new RoleEntity();
            role.setCode(code);
            role.setName(name);
            role.setFeatureIds(new ArrayList<>(featureIds));
            return roles.save(role);
        }
        if (refresh && !featureIds.equals(role.getFeatureIds())) {
            role.setFeatureIds(new ArrayList<>(featureIds));
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
