package com.drdo.jayant.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.drdo.jayant.domain.AppUser;
import com.drdo.jayant.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public List<UserResponse> list() {
        return users.list().stream().map(UserResponse::of).toList();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return UserResponse.of(users.get(id));
    }

    @PatchMapping("/{id}")
    public UserResponse patch(@PathVariable Long id, @Valid @RequestBody UserPatchRequest request) {
        return UserResponse.of(users.patch(id, request.name(), request.password(), request.roleIds(),
                request.active(), request.administeredTeamId(), request.themeId()));
    }

    @PatchMapping("/me/theme")
    public UserResponse chooseTheme(@Valid @RequestBody ThemeChoice request) {
        return UserResponse.of(users.chooseTheme(request.themeId()));
    }

    @PostMapping("/mark-from-node")
    public ResponseEntity<UserResponse> markFromNode(@Valid @RequestBody MarkRequest request) {
        AppUser user = users.markFromNode(request.standardOrbatId(), request.standardNodeId(), request.name(),
                request.password(), request.roleIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.of(user));
    }

    /** Absent fields stay unchanged. */
    public record UserPatchRequest(
            @Size(min = 1, max = 120) String name,
            @Size(min = 3, max = 72) String password,
            List<Long> roleIds,
            Boolean active,
            Long administeredTeamId,
            Long themeId) {
    }

    public record ThemeChoice(@NotNull Long themeId) {
    }

    public record MarkRequest(
            @NotNull Long standardOrbatId,
            @NotBlank @Size(max = 120) String standardNodeId,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 3, max = 72) String password,
            @NotEmpty List<Long> roleIds) {
    }

    /** Never carries the password hash. */
    public record UserResponse(Long id, String name, List<Long> roleIds, Long administeredTeamId, boolean active,
            Long themeId, Long standardOrbatId, String standardNodeId, Long playingOrbatId, String playingNodeId,
            Instant lastLoginAt, Instant createdAt, Instant updatedAt) {

        static UserResponse of(AppUser user) {
            return new UserResponse(user.getId(), user.getName(),
                    user.getRoleIds() == null ? List.of() : user.getRoleIds(), user.getAdministeredTeamId(),
                    user.isActive(), user.getThemeId(), user.getStandardOrbatId(), user.getStandardNodeId(),
                    user.getPlayingOrbatId(), user.getPlayingNodeId(), user.getLastLoginAt(), user.getCreatedAt(),
                    user.getUpdatedAt());
        }
    }
}
