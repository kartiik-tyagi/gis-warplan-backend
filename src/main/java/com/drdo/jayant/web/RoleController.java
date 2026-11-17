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
import com.drdo.jayant.domain.FeatureEntity;
import com.drdo.jayant.domain.RoleEntity;
import com.drdo.jayant.service.AccessService;
import com.drdo.jayant.service.RoleService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Features are read-only; roles are managed with MANAGE_ROLES. */
@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleService roles;
    private final AccessService access;

    public RoleController(RoleService roles, AccessService access) {
        this.roles = roles;
        this.access = access;
    }

    @GetMapping("/features")
    public List<FeatureResponse> features() {
        access.requireAnyFeature(FeatureCodes.MANAGE_ROLES);
        return roles.listFeatures().stream().map(FeatureResponse::of).toList();
    }

    @GetMapping("/roles")
    public List<RoleResponse> list() {
        access.requireAnyFeature(FeatureCodes.MANAGE_ROLES, FeatureCodes.MANAGE_USERS, FeatureCodes.MARK_AS_USER);
        return roles.list().stream().map(RoleResponse::of).toList();
    }

    @GetMapping("/roles/{id}")
    public RoleResponse get(@PathVariable Long id) {
        access.requireAnyFeature(FeatureCodes.MANAGE_ROLES, FeatureCodes.MANAGE_USERS, FeatureCodes.MARK_AS_USER);
        return RoleResponse.of(roles.get(id));
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        access.requireFeature(FeatureCodes.MANAGE_ROLES);
        RoleEntity role = roles.create(request.code(), request.name(), request.description(), request.featureIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleResponse.of(role));
    }

    @PutMapping("/roles/{id}")
    public RoleResponse update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        access.requireFeature(FeatureCodes.MANAGE_ROLES);
        return RoleResponse.of(roles.update(id, request.code(), request.name(), request.description(),
                request.featureIds()));
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        access.requireFeature(FeatureCodes.MANAGE_ROLES);
        roles.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record RoleRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$") String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            List<Long> featureIds) {
    }

    public record RoleResponse(Long id, String code, String name, String description, List<Long> featureIds,
            Instant createdAt, Instant updatedAt) {

        static RoleResponse of(RoleEntity role) {
            return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                    role.getFeatureIds() == null ? List.of() : role.getFeatureIds(), role.getCreatedAt(),
                    role.getUpdatedAt());
        }
    }

    public record FeatureResponse(Long id, String code, String name, String description) {

        static FeatureResponse of(FeatureEntity feature) {
            return new FeatureResponse(feature.getId(), feature.getCode(), feature.getName(), feature.getDescription());
        }
    }
}
