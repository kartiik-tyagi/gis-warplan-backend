package com.gis.warplan.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.enums.EnumCatalog.ListDef;
import com.gis.warplan.domain.enums.EnumMutable;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.EnumCatalogService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dropdown lists. Management needs MANAGE_ENUMS. Active choices are readable by any signed-in user. */
@RestController
@RequestMapping("/api/v1/enums")
public class EnumController {

    private final EnumCatalogService enums;
    private final AccessService access;

    public EnumController(EnumCatalogService enums, AccessService access) {
        this.enums = enums;
        this.access = access;
    }

    @GetMapping
    public List<EnumListResponse> lists() {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ENUMS);
        return enums.lists().stream()
                .map(list -> new EnumListResponse(list.key(), list.label(), list.sideSpecific()))
                .toList();
    }

    @GetMapping("/{list}/choices")
    public List<EnumValueResponse> choices(@PathVariable String list, @RequestParam(required = false) Long sideId) {
        access.requirePrincipal();
        return enums.choices(enums.requireList(list), sideId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{list}")
    public List<EnumValueResponse> list(@PathVariable String list) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ENUMS);
        return enums.list(enums.requireList(list)).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{list}/{code}")
    public EnumValueResponse get(@PathVariable String list, @PathVariable String code,
            @RequestParam(required = false) Long sideId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ENUMS);
        return toResponse(enums.get(enums.requireList(list), code, sideId));
    }

    @PostMapping("/{list}")
    public ResponseEntity<EnumValueResponse> create(@PathVariable String list, @Valid @RequestBody EnumWriteRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ENUMS);
        ListDef def = enums.requireList(list);
        EnumMutable created = enums.create(def, request.code(), request.label(), request.sortOrder(), request.active(),
                request.sideId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PutMapping("/{list}/{code}")
    public EnumValueResponse update(@PathVariable String list, @PathVariable String code,
            @RequestParam(required = false) Long sideId, @Valid @RequestBody EnumWriteRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ENUMS);
        ListDef def = enums.requireList(list);
        return toResponse(enums.update(def, code, sideId, request.code(), request.label(), request.sortOrder(),
                request.active(), request.sideId()));
    }

    public record EnumListResponse(String key, String label, boolean sideSpecific) {
    }

    public record EnumWriteRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String label,
            @NotNull Integer sortOrder,
            @NotNull Boolean active,
            Long sideId) {
    }

    public record EnumValueResponse(String code, String label, int sortOrder, boolean active, Long sideId, String sideName) {
    }

    private EnumValueResponse toResponse(EnumMutable row) {
        return new EnumValueResponse(row.getCode(), row.getLabel(), row.getSortOrder(), row.isActive(), row.getSideId(),
                enums.sideName(row.getSideId()));
    }
}
