package com.drdo.jayant.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.drdo.jayant.domain.FunctionalityCodes;
import com.drdo.jayant.domain.Theme;
import com.drdo.jayant.service.AccessService;
import com.drdo.jayant.service.ThemeService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reading themes only needs a login (theme resolve / CHOOSE_THEME); changes need MANAGE_THEMES. */
@RestController
@RequestMapping("/api/v1/themes")
public class ThemeController {

    private final ThemeService themes;
    private final AccessService access;

    public ThemeController(ThemeService themes, AccessService access) {
        this.themes = themes;
        this.access = access;
    }

    @GetMapping
    public List<ThemeResponse> list() {
        access.requirePrincipal();
        return themes.list().stream().map(ThemeResponse::of).toList();
    }

    @GetMapping("/{id}")
    public ThemeResponse get(@PathVariable Long id) {
        access.requirePrincipal();
        return ThemeResponse.of(themes.get(id));
    }

    @PostMapping
    public ResponseEntity<ThemeResponse> create(@Valid @RequestBody ThemeRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_THEMES);
        Theme theme = themes.create(request.name(), request.description(), request.defaultTheme(), request.configJson());
        return ResponseEntity.status(HttpStatus.CREATED).body(ThemeResponse.of(theme));
    }

    @PutMapping("/{id}")
    public ThemeResponse update(@PathVariable Long id, @Valid @RequestBody ThemeRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_THEMES);
        return ThemeResponse.of(themes.update(id, request.name(), request.description(), request.defaultTheme(),
                request.configJson()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_THEMES);
        themes.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record ThemeRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            Boolean defaultTheme,
            Map<String, Object> configJson) {
    }

    public record ThemeResponse(Long id, String name, String description, boolean defaultTheme,
            Map<String, Object> configJson, Instant createdAt, Instant updatedAt) {

        static ThemeResponse of(Theme theme) {
            return new ThemeResponse(theme.getId(), theme.getName(), theme.getDescription(), theme.isDefaultTheme(),
                    theme.getConfigJson(), theme.getCreatedAt(), theme.getUpdatedAt());
        }
    }
}
