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
import com.gis.warplan.service.AssetService;

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
    private final AssetService store;
    private final AccessService access;

    public AssetManagementController(AssetManagementService assets, AssetService store, AccessService access) {
        this.assets = assets;
        this.store = store;
        this.access = access;
    }

    @GetMapping
    public AssetResponse get() {
        return AssetResponse.of(assets.load(), currentUserKey());
    }

    @PutMapping
    public AssetResponse update(@Valid @RequestBody SettingsRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        assets.updatePath(request.path());
        if (request.iconMaxBytes() != null || request.imageMaxBytes() != null || request.documentMaxBytes() != null) {
            assets.updateLimits(request.iconMaxBytes(), request.imageMaxBytes(), request.documentMaxBytes());
        }
        return AssetResponse.of(assets.load(), currentUserKey());
    }

    @PutMapping("/labels")
    public AssetResponse setLabel(@Valid @RequestBody LabelRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        store.setLabelByKey(request.path(), request.label());
        return AssetResponse.of(assets.load(), currentUserKey());
    }

    @DeleteMapping("/labels")
    public AssetResponse clearLabel(@RequestParam("path") String path) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        store.clearLabelByKey(path);
        return AssetResponse.of(assets.load(), currentUserKey());
    }

    private String currentUserKey() {
        return String.valueOf(access.requirePrincipal().userId());
    }

    public record SettingsRequest(
            @NotBlank @Size(max = 500) String path,
            Long iconMaxBytes,
            Long imageMaxBytes,
            Long documentMaxBytes) {
    }

    public record LabelRequest(@NotBlank @Size(max = 300) String path, @NotBlank @Size(max = 120) String label) {
    }

    public record AssetResponse(Long id, String path, Map<String, String> labels, long iconMaxBytes, long imageMaxBytes,
            long documentMaxBytes, long lastUpdated, Long updatedBy) {

        static AssetResponse of(AssetManagement row, String userKey) {
            return new AssetResponse(
                    row.getId(),
                    row.getPath(),
                    AssetService.flattenLabels(row.getLabels()),
                    row.getIconMaxBytes() == null ? AssetService.DEFAULT_ICON_MAX : row.getIconMaxBytes(),
                    row.getImageMaxBytes() == null ? AssetService.DEFAULT_IMAGE_MAX : row.getImageMaxBytes(),
                    row.getDocumentMaxBytes() == null ? AssetService.DEFAULT_DOCUMENT_MAX : row.getDocumentMaxBytes(),
                    row.getLastUpdated() == null ? 0L : row.getLastUpdated().toEpochMilli(),
                    row.getUpdatedBy());
        }
    }
}
