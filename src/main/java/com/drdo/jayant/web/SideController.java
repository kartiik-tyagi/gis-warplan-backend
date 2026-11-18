package com.drdo.jayant.web;

import java.time.Instant;
import java.util.List;

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
import com.drdo.jayant.domain.Side;
import com.drdo.jayant.service.AccessService;
import com.drdo.jayant.service.SideService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Any signed-in user can read sides (names and colours are needed to draw ORBATs); changes need MANAGE_SIDES.
 * A side administrator may only update the side they administer.
 */
@RestController
@RequestMapping("/api/v1/sides")
public class SideController {

    private final SideService sides;
    private final AccessService access;

    public SideController(SideService sides, AccessService access) {
        this.sides = sides;
        this.access = access;
    }

    @GetMapping
    public List<SideResponse> list() {
        access.requirePrincipal();
        return sides.list().stream().map(SideResponse::of).toList();
    }

    @GetMapping("/{id}")
    public SideResponse get(@PathVariable Long id) {
        access.requirePrincipal();
        return SideResponse.of(sides.get(id));
    }

    @PostMapping
    public ResponseEntity<SideResponse> create(@Valid @RequestBody SideRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SIDES);
        Side side = sides.create(request.name(), request.description(), request.colour(), request.icon(),
                request.themeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(SideResponse.of(side));
    }

    @PutMapping("/{id}")
    public SideResponse update(@PathVariable Long id, @Valid @RequestBody SideRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SIDES);
        return SideResponse.of(sides.update(id, request.name(), request.description(), request.colour(),
                request.icon(), request.themeId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SIDES);
        sides.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record SideRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String colour,
            @Size(max = 300) String icon,
            Long themeId) {
    }

    public record SideResponse(Long id, String name, String kind, String description, String colour, String icon,
            Long themeId, Long gameId, Long scenarioId, Instant createdAt, Instant updatedAt) {

        public static SideResponse of(Side side) {
            return new SideResponse(side.getId(), side.getName(), side.getKind(), side.getDescription(),
                    side.getColour(), side.getIcon(), side.getThemeId(), side.getGameId(), side.getScenarioId(),
                    side.getCreatedAt(), side.getUpdatedAt());
        }
    }
}
