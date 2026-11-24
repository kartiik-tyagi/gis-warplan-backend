package com.gis.warplan.web;

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

import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.Functionality;
import com.gis.warplan.domain.RoleEntity;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.RoleService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Functionalities are read-only; roles are managed with MANAGE_ROLES. Role codes are internal only. */
@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleService roles;
    private final AccessService access;

    public RoleController(RoleService roles, AccessService access) {
        this.roles = roles;
        this.access = access;
    }

    @GetMapping("/functionalities")
    public List<FunctionalityResponse> functionalities() {
        access.requireAnyFunctionality(FunctionalityCodes.MANAGE_ROLES);
        return roles.listFunctionalities().stream().map(FunctionalityResponse::of).toList();
    }

    @GetMapping("/roles")
    public List<RoleResponse> list() {
        access.requireAnyFunctionality(FunctionalityCodes.MANAGE_ROLES, FunctionalityCodes.MANAGE_USERS, FunctionalityCodes.MARK_AS_USER);
        return roles.list().stream().map(RoleResponse::of).toList();
    }

    @GetMapping("/roles/{id}")
    public RoleResponse get(@PathVariable Long id) {
        access.requireAnyFunctionality(FunctionalityCodes.MANAGE_ROLES, FunctionalityCodes.MANAGE_USERS, FunctionalityCodes.MARK_AS_USER);
        return RoleResponse.of(roles.get(id));
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ROLES);
        RoleEntity role = roles.create(request.name(), request.description(), request.functionalityIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleResponse.of(role));
    }

    @PutMapping("/roles/{id}")
    public RoleResponse update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ROLES);
        return RoleResponse.of(roles.update(id, request.name(), request.description(), request.functionalityIds()));
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ROLES);
        roles.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record RoleRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            List<Long> functionalityIds) {
    }

    public record RoleResponse(Long id, String name, String description, List<Long> functionalityIds,
            boolean standard, Instant createdAt, Instant updatedAt) {

        static RoleResponse of(RoleEntity role) {
            return new RoleResponse(role.getId(), role.getName(), role.getDescription(),
                    role.getFunctionalityIds() == null ? List.of() : role.getFunctionalityIds(), role.isStandard(),
                    role.getCreatedAt(), role.getUpdatedAt());
        }
    }

    public record FunctionalityResponse(Long id, String code, String name, String description) {

        static FunctionalityResponse of(Functionality functionality) {
            return new FunctionalityResponse(functionality.getId(), functionality.getCode(), functionality.getName(), functionality.getDescription());
        }
    }
}
