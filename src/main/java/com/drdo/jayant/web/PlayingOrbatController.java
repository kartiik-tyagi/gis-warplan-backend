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

import com.drdo.jayant.domain.PlayingOrbat;
import com.drdo.jayant.service.PlayingOrbatService;
import com.drdo.jayant.service.PlayingOrbatService.Alliance;
import com.drdo.jayant.web.TeamController.TeamResponse;

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

    @GetMapping("/scenarios/{scenarioId}/playing-orbats")
    public List<PlayingOrbatResponse> list(@PathVariable Long scenarioId) {
        return playingOrbats.list(scenarioId).stream().map(PlayingOrbatResponse::of).toList();
    }

    @PostMapping("/scenarios/{scenarioId}/playing-orbats/replica")
    public ResponseEntity<PlayingOrbatResponse> replica(@PathVariable Long scenarioId,
            @Valid @RequestBody ReplicaRequest request) {
        PlayingOrbat orbat = playingOrbats.replicate(scenarioId, request.teamId());
        return ResponseEntity.status(HttpStatus.CREATED).body(PlayingOrbatResponse.of(orbat));
    }

    @GetMapping("/playing-orbats/{id}")
    public PlayingOrbatResponse get(@PathVariable Long id) {
        return PlayingOrbatResponse.of(playingOrbats.get(id));
    }

    @PutMapping("/playing-orbats/{id}")
    public PlayingOrbatResponse update(@PathVariable Long id, @Valid @RequestBody PlayingOrbatRequest request) {
        return PlayingOrbatResponse.of(playingOrbats.update(id, request.orbatJson(), request.metadata(),
                request.version()));
    }

    @DeleteMapping("/playing-orbats/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        playingOrbats.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scenarios/{scenarioId}/alliances")
    public List<AllianceResponse> alliances(@PathVariable Long scenarioId) {
        return playingOrbats.listAlliances(scenarioId).stream().map(AllianceResponse::of).toList();
    }

    @PostMapping("/scenarios/{scenarioId}/alliances")
    public ResponseEntity<AllianceResponse> createAlliance(@PathVariable Long scenarioId,
            @Valid @RequestBody AllianceRequest request) {
        Alliance alliance = playingOrbats.createAlliance(scenarioId, request.name(), request.colour(),
                request.icon(), request.themeId(), request.memberTeamIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(AllianceResponse.of(alliance));
    }

    public record ReplicaRequest(@NotNull Long teamId) {
    }

    /** Only the fields sent are changed; metadata keys are merged into the stored metadata. */
    public record PlayingOrbatRequest(Map<String, Object> orbatJson, Map<String, Object> metadata,
            @NotNull Long version) {
    }

    public record PlayingOrbatResponse(Long id, String name, Long teamId, Long standardOrbatId, Long scenarioId,
            Long version, Map<String, Object> orbatJson, Map<String, Object> metadata, Instant createdAt,
            Instant updatedAt) {

        static PlayingOrbatResponse of(PlayingOrbat orbat) {
            return new PlayingOrbatResponse(orbat.getId(), orbat.getName(), orbat.getTeamId(),
                    orbat.getStandardOrbatId(), orbat.getScenarioId(), orbat.getVersion(), orbat.getOrbatJson(),
                    orbat.getMetadata(), orbat.getCreatedAt(), orbat.getUpdatedAt());
        }
    }

    public record AllianceRequest(
            @NotBlank @Size(max = 120) String name,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String colour,
            @Size(max = 300) String icon,
            Long themeId,
            @NotEmpty List<Long> memberTeamIds) {
    }

    public record AllianceResponse(TeamResponse team, List<Long> memberTeamIds, Long playingOrbatId) {

        static AllianceResponse of(Alliance alliance) {
            return new AllianceResponse(TeamResponse.of(alliance.team()), alliance.memberTeamIds(),
                    alliance.playingOrbat().getId());
        }
    }
}
