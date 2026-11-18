package com.gis.warplan.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.Theme;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.ScenarioRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.repository.ThemeRepository;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.NotFoundException;

@Service
public class ThemeService {

    private final ThemeRepository themes;
    private final AppUserRepository users;
    private final ScenarioRepository scenarios;
    private final SideRepository sides;
    private final AuditService audit;

    public ThemeService(ThemeRepository themes, AppUserRepository users, ScenarioRepository scenarios,
            SideRepository sides, AuditService audit) {
        this.themes = themes;
        this.users = users;
        this.scenarios = scenarios;
        this.sides = sides;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Theme> list() {
        return themes.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Theme get(Long id) {
        return themes.findById(id).orElseThrow(() -> new NotFoundException("Theme not found."));
    }

    @Transactional
    public Theme create(String name, String description, Boolean defaultTheme, Map<String, Object> configJson) {
        String trimmed = name.trim();
        if (themes.findByName(trimmed).isPresent()) {
            throw new ConflictException("A theme with this name already exists.");
        }
        Theme theme = new Theme();
        theme.setName(trimmed);
        theme.setDescription(description);
        theme.setConfigJson(configJson);
        boolean makeDefault = Boolean.TRUE.equals(defaultTheme) || themes.count() == 0;
        if (makeDefault) {
            clearDefaults();
        }
        theme.setDefaultTheme(makeDefault);
        Theme saved = themes.save(theme);
        audit.record("CREATE", "Theme", saved.getId(), "Theme " + saved.getName() + " created", Map.of());
        return saved;
    }

    @Transactional
    public Theme update(Long id, String name, String description, Boolean defaultTheme, Map<String, Object> configJson) {
        Theme theme = get(id);
        String trimmed = name.trim();
        themes.findByName(trimmed).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("A theme with this name already exists.");
        });
        if (Boolean.FALSE.equals(defaultTheme) && theme.isDefaultTheme()) {
            throw new ConflictException("Pick another theme as the default instead of unsetting this one.");
        }
        if (Boolean.TRUE.equals(defaultTheme) && !theme.isDefaultTheme()) {
            clearDefaults();
            theme.setDefaultTheme(true);
        }
        theme.setName(trimmed);
        theme.setDescription(description);
        theme.setConfigJson(configJson);
        Theme saved = themes.save(theme);
        audit.record("UPDATE", "Theme", saved.getId(), "Theme " + saved.getName() + " updated", Map.of());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Theme theme = get(id);
        if (theme.isDefaultTheme()) {
            throw new ConflictException("The default theme cannot be deleted.");
        }
        if (users.countByThemeId(id) > 0 || scenarios.countByThemeId(id) > 0 || sides.countByThemeId(id) > 0) {
            throw new ConflictException("The theme is in use.");
        }
        themes.delete(theme);
        audit.record("DELETE", "Theme", id, "Theme " + theme.getName() + " deleted", Map.of());
    }

    @Transactional(readOnly = true)
    public void requireExists(Long id) {
        if (id != null && !themes.existsById(id)) {
            throw new BadRequestException("Theme " + id + " does not exist.");
        }
    }

    private void clearDefaults() {
        for (Theme other : themes.findAll()) {
            if (other.isDefaultTheme()) {
                other.setDefaultTheme(false);
                themes.saveAndFlush(other);
            }
        }
    }
}
