package com.gis.warplan.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gis.warplan.domain.StandardOrbat;
import com.gis.warplan.service.StandardOrbatService;
import com.gis.warplan.service.StandardOrbatService.Created;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/sides/{sideId}/standard-orbat")
public class StandardOrbatController {

    private final StandardOrbatService orbats;

    public StandardOrbatController(StandardOrbatService orbats) {
        this.orbats = orbats;
    }

    @GetMapping
    public StandardOrbatResponse get(@PathVariable Long sideId) {
        return StandardOrbatResponse.of(orbats.get(sideId));
    }

    /** Creates the empty root node when the side has no standard ORBAT yet (201), otherwise returns it (200). */
    @PostMapping
    public ResponseEntity<StandardOrbatResponse> create(@PathVariable Long sideId) {
        Created result = orbats.create(sideId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(StandardOrbatResponse.of(result.orbat()));
    }

    @PutMapping
    public StandardOrbatResponse update(@PathVariable Long sideId, @Valid @RequestBody StandardOrbatRequest request) {
        return StandardOrbatResponse.of(orbats.update(sideId, request.orbatJson(), request.metadata(),
                request.version()));
    }

    public record StandardOrbatRequest(@NotNull Map<String, Object> orbatJson, Map<String, Object> metadata,
            @NotNull Long version) {
    }

    public record StandardOrbatResponse(Long id, String name, Long sideId, Long version,
            Map<String, Object> orbatJson, Map<String, Object> metadata, Instant createdAt, Instant updatedAt) {

        static StandardOrbatResponse of(StandardOrbat orbat) {
            return new StandardOrbatResponse(orbat.getId(), orbat.getName(), orbat.getSideId(), orbat.getVersion(),
                    orbat.getOrbatJson(), orbat.getMetadata(), orbat.getCreatedAt(), orbat.getUpdatedAt());
        }
    }
}
