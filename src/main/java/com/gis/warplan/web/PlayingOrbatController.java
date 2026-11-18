package com.gis.warplan.web;

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

import com.gis.warplan.domain.PlayingOrbat;
import com.gis.warplan.service.PlayingOrbatService;
import com.gis.warplan.service.PlayingOrbatService.Alliance;
import com.gis.warplan.web.SideController.SideResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
public class PlayingOrbatController {

    private final PlayingOrbatService playingOrbats;

    public PlayingOrbatController(PlayingOrbatService playingOrbats) {
        this.playingOrbats = playingOrbats;
    }

    @GetMapping("/games/{gameId}/playing-orbats")
    public List<PlayingOrbatResponse> list(@PathVariable Long gameId) {
        return playingOrbats.list(gameId).stream().map(PlayingOrbatResponse::of).toList();
    }

    @PostMapping("/games/{gameId}/playing-orbats/replica")
    public ResponseEntity<PlayingOrbatResponse> replica(@PathVariable Long gameId,
            @Valid @RequestBody ReplicaRequest request) {
        PlayingOrbat orbat = playingOrbats.replicate(gameId, request.sideId());
        return ResponseEntity.status(HttpStatus.CREATED).body(PlayingOrbatResponse.of(orbat));
    }

    @GetMapping("/games/{gameId}/playing-orbats/{id}")
    public PlayingOrbatResponse get(@PathVariable Long gameId, @PathVariable Long id) {
        return PlayingOrbatResponse.of(playingOrbats.get(gameId, id));
    }

    @PutMapping("/games/{gameId}/playing-orbats/{id}")
    public PlayingOrbatResponse update(@PathVariable Long gameId, @PathVariable Long id,
            @Valid @RequestBody PlayingOrbatRequest request) {
        return PlayingOrbatResponse.of(playingOrbats.update(gameId, id, request.orbatJson(), request.metadata(),
                request.version()));
    }

    @DeleteMapping("/games/{gameId}/playing-orbats/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long gameId, @PathVariable Long id) {
        playingOrbats.delete(gameId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/games/{gameId}/alliances")
    public List<AllianceResponse> alliances(@PathVariable Long gameId) {
        return playingOrbats.listAlliances(gameId).stream().map(AllianceResponse::of).toList();
    }

    @PostMapping("/games/{gameId}/alliances")
    public ResponseEntity<AllianceResponse> createAlliance(@PathVariable Long gameId,
            @Valid @RequestBody AllianceRequest request) {
        Alliance alliance = playingOrbats.createAlliance(gameId, request.name(), request.colour(),
                request.icon(), request.themeId(), request.memberSideIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(AllianceResponse.of(alliance));
    }

    public record ReplicaRequest(@NotNull Long sideId) {
    }

    /** Only the fields sent are changed; metadata keys are merged into the stored metadata. */
    public record PlayingOrbatRequest(Map<String, Object> orbatJson, Map<String, Object> metadata,
            @NotNull Long version) {
    }

    public record PlayingOrbatResponse(Long id, String name, Long sideId, Long standardOrbatId, Long gameId,
            Long scenarioId, Long version, Map<String, Object> orbatJson, Map<String, Object> metadata, Instant createdAt,
            Instant updatedAt) {

        static PlayingOrbatResponse of(PlayingOrbat orbat) {
            return new PlayingOrbatResponse(orbat.getId(), orbat.getName(), orbat.getSideId(),
                    orbat.getStandardOrbatId(), orbat.getGameId(), orbat.getScenarioId(), orbat.getVersion(), orbat.getOrbatJson(),
                    orbat.getMetadata(), orbat.getCreatedAt(), orbat.getUpdatedAt());
        }
    }

    public record AllianceRequest(
            @NotBlank @Size(max = 120) String name,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String colour,
            @Size(max = 300) String icon,
            Long themeId,
            @NotEmpty List<Long> memberSideIds) {
    }

    public record AllianceResponse(SideResponse side, List<Long> memberSideIds, Long playingOrbatId) {

        static AllianceResponse of(Alliance alliance) {
            return new AllianceResponse(SideResponse.of(alliance.side()), alliance.memberSideIds(),
                    alliance.playingOrbat().getId());
        }
    }
}
