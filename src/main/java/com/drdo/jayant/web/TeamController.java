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

import com.drdo.jayant.domain.FeatureCodes;
import com.drdo.jayant.domain.Team;
import com.drdo.jayant.service.AccessService;
import com.drdo.jayant.service.TeamService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Any signed-in user can read teams (names and colours are needed to draw ORBATs); changes need MANAGE_TEAMS.
 * A team administrator may only update the team they administer.
 */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teams;
    private final AccessService access;

    public TeamController(TeamService teams, AccessService access) {
        this.teams = teams;
        this.access = access;
    }

    @GetMapping
    public List<TeamResponse> list() {
        access.requirePrincipal();
        return teams.list().stream().map(TeamResponse::of).toList();
    }

    @GetMapping("/{id}")
    public TeamResponse get(@PathVariable Long id) {
        access.requirePrincipal();
        return TeamResponse.of(teams.get(id));
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody TeamRequest request) {
        access.requireFeature(FeatureCodes.MANAGE_TEAMS);
        Team team = teams.create(request.name(), request.description(), request.colour(), request.icon(),
                request.themeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(TeamResponse.of(team));
    }

    @PutMapping("/{id}")
    public TeamResponse update(@PathVariable Long id, @Valid @RequestBody TeamRequest request) {
        access.requireFeature(FeatureCodes.MANAGE_TEAMS);
        return TeamResponse.of(teams.update(id, request.name(), request.description(), request.colour(),
                request.icon(), request.themeId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        access.requireFeature(FeatureCodes.MANAGE_TEAMS);
        teams.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record TeamRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String colour,
            @Size(max = 300) String icon,
            Long themeId) {
    }

    public record TeamResponse(Long id, String name, String kind, String description, String colour, String icon,
            Long themeId, Instant createdAt, Instant updatedAt) {

        public static TeamResponse of(Team team) {
            return new TeamResponse(team.getId(), team.getName(), team.getKind(), team.getDescription(),
                    team.getColour(), team.getIcon(), team.getThemeId(), team.getCreatedAt(), team.getUpdatedAt());
        }
    }
}
