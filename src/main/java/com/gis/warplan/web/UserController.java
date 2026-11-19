package com.gis.warplan.web;

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

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.service.ThemeAllotmentService;
import com.gis.warplan.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService users;
    private final ThemeAllotmentService allotment;

    public UserController(UserService users, ThemeAllotmentService allotment) {
        this.users = users;
        this.allotment = allotment;
    }

    @GetMapping
    public List<UserResponse> list() {
        return users.list().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return toResponse(users.get(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        AppUser user = users.create(request.name(), request.password(), request.roleIds(),
                request.administeredSideId(), request.themeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @PatchMapping("/{id}")
    public UserResponse patch(@PathVariable Long id, @Valid @RequestBody UserPatchRequest request) {
        return toResponse(users.patch(id, request.name(), request.password(), request.roleIds(),
                request.active(), request.administeredSideId(), request.themeId(), request.clearTheme()));
    }

    @PatchMapping("/me/theme")
    public UserResponse chooseTheme(@Valid @RequestBody ThemeChoice request) {
        return toResponse(users.chooseTheme(request.themeId()));
    }

    @PostMapping("/mark-from-node")
    public ResponseEntity<UserResponse> markFromNode(@Valid @RequestBody MarkRequest request) {
        AppUser user = users.markFromNode(request.standardOrbatId(), request.standardNodeId(), request.name(),
                request.password(), request.roleIds(), request.themeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 3, max = 72) String password,
            @NotEmpty List<Long> roleIds,
            Long administeredSideId,
            Long themeId) {
    }

    /** Absent fields stay unchanged. {@code clearTheme} drops an override so the user inherits the side theme. */
    public record UserPatchRequest(
            @Size(min = 1, max = 120) String name,
            @Size(min = 3, max = 72) String password,
            List<Long> roleIds,
            Boolean active,
            Long administeredSideId,
            Long themeId,
            Boolean clearTheme) {
    }

    public record ThemeChoice(@NotNull Long themeId) {
    }

    public record MarkRequest(
            @NotNull Long standardOrbatId,
            @NotBlank @Size(max = 120) String standardNodeId,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 3, max = 72) String password,
            @NotEmpty List<Long> roleIds,
            Long themeId) {
    }

    /**
     * {@code themeId} is the exclusive override, null when the account inherits. {@code displayThemeId} is the
     * theme the account actually sees. Never carries the password hash.
     */
    public record UserResponse(Long id, String name, List<Long> roleIds, Long administeredSideId, boolean active,
            Long themeId, Long homeSideId, Long displayThemeId, Long standardOrbatId, String standardNodeId,
            Long playingOrbatId, String playingNodeId, Instant lastLoginAt, Instant createdAt, Instant updatedAt) {
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getName(),
                user.getRoleIds() == null ? List.of() : user.getRoleIds(), user.getAdministeredSideId(),
                user.isActive(), user.getThemeId(), allotment.homeSideId(user), allotment.displayThemeId(user),
                user.getStandardOrbatId(), user.getStandardNodeId(), user.getPlayingOrbatId(), user.getPlayingNodeId(),
                user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
