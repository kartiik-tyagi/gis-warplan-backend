package com.gis.warplan.web;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gis.warplan.domain.AssetManagement;
import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.AssetManagementService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The single asset-management row: the base icon folder the UI reads from and the per-icon labels.
 * Any signed-in user may read it (the icon picker needs the labels); changes need MANAGE_ICONS.
 */
@RestController
@RequestMapping("/api/v1/asset-management")
public class AssetManagementController {

    private final AssetManagementService assets;
    private final AccessService access;

    public AssetManagementController(AssetManagementService assets, AccessService access) {
        this.assets = assets;
        this.access = access;
    }

    @GetMapping
    public AssetResponse get() {
        return AssetResponse.of(assets.load(), currentUserKey());
    }

    @PutMapping
    public AssetResponse updatePath(@Valid @RequestBody PathRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        return AssetResponse.of(assets.updatePath(request.path()), currentUserKey());
    }

    @PutMapping("/labels")
    public AssetResponse setLabel(@Valid @RequestBody LabelRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        return AssetResponse.of(assets.setLabel(request.path(), request.label()), currentUserKey());
    }

    @DeleteMapping("/labels")
    public AssetResponse clearLabel(@RequestParam("path") String path) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        return AssetResponse.of(assets.removeLabel(path), currentUserKey());
    }

    private String currentUserKey() {
        return String.valueOf(access.requirePrincipal().userId());
    }

    public record PathRequest(@NotBlank @Size(max = 500) String path) {
    }

    public record LabelRequest(@NotBlank @Size(max = 300) String path, @NotBlank @Size(max = 120) String label) {
    }

    public record AssetResponse(Long id, String path, Map<String, String> labels, long lastUpdated, Long updatedBy) {

        static AssetResponse of(AssetManagement row, String userKey) {
            return new AssetResponse(
                    row.getId(),
                    row.getPath(),
                    AssetManagementService.labelsForUser(row.getLabels(), userKey),
                    row.getLastUpdated() == null ? 0L : row.getLastUpdated().toEpochMilli(),
                    row.getUpdatedBy());
        }
    }
}
