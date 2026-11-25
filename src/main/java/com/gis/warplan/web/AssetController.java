package com.gis.warplan.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gis.warplan.domain.ApplicationSlot;
import com.gis.warplan.domain.Asset;
import com.gis.warplan.domain.AssetKind;
import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.AssetService;
import com.gis.warplan.service.AssetService.AssetContent;
import com.gis.warplan.service.AuditService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assets;
    private final AccessService access;
    private final AuditService audit;

    public AssetController(AssetService assets, AccessService access, AuditService audit) {
        this.assets = assets;
        this.access = access;
        this.audit = audit;
    }

    @GetMapping
    public List<AssetResponse> list(@RequestParam("kind") String kind) {
        access.requirePrincipal();
        AssetKind parsed = AssetKind.fromQuery(kind);
        if (parsed == null) {
            throw new BadRequestException("Choose icons, images, or documents.");
        }
        return assets.list(parsed).stream().map(AssetResponse::of).toList();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<AssetResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kind") String kind,
            @RequestParam(value = "label", required = false) String label) throws IOException {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        if (file == null) {
            throw new BadRequestException("Choose a file to upload.");
        }
        AssetKind parsed = AssetKind.fromQuery(kind);
        Asset stored = assets.storeUpload(parsed, file.getOriginalFilename(), file.getBytes(), label);
        audit.record("UPLOAD", "Asset", stored.getId(), "Asset stored " + stored.getStorageKey(),
                Map.of("path", stored.getStorageKey(), "kind", String.valueOf(parsed)));
        return ResponseEntity.status(HttpStatus.CREATED).body(AssetResponse.of(stored));
    }

    @GetMapping("/content")
    public ResponseEntity<byte[]> content(@RequestParam("path") String path) {
        access.requirePrincipal();
        return body(assets.readByKey(path));
    }

    @PutMapping("/{id}/label")
    public AssetResponse label(@PathVariable Long id, @Valid @RequestBody LabelRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        Asset saved = assets.setLabel(id, request.label());
        audit.record("UPDATE", "Asset", id, "Asset label set", Map.of("label", saved.displayName()));
        return AssetResponse.of(saved);
    }

    @DeleteMapping("/{id}/label")
    public AssetResponse clearLabel(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        return AssetResponse.of(assets.clearLabel(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        assets.delete(id);
        audit.record("DELETE", "Asset", id, "Asset deleted", Map.of());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/application/{slot}/content")
    public ResponseEntity<byte[]> application(@PathVariable String slot) {
        ApplicationSlot parsed = ApplicationSlot.fromKey(slot);
        if (parsed == null) {
            throw new NotFoundException("That picture was not found.");
        }
        AssetContent content = assets.readApplication(parsed);
        if (content == null) {
            throw new NotFoundException("That picture was not found.");
        }
        return body(content);
    }

    @PutMapping(path = "/application/{slot}", consumes = "multipart/form-data")
    public AssetResponse replaceApplication(@PathVariable String slot, @RequestParam("file") MultipartFile file)
            throws IOException {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        ApplicationSlot parsed = ApplicationSlot.fromKey(slot);
        if (file == null) {
            throw new BadRequestException("Choose a file to upload.");
        }
        Asset stored = assets.replaceApplication(parsed, file.getOriginalFilename(), file.getBytes());
        audit.record("UPDATE", "Asset", stored.getId(), "Application picture replaced " + parsed, Map.of("slot", parsed.key()));
        return AssetResponse.of(stored);
    }

    private static ResponseEntity<byte[]> body(AssetContent content) {
        String disposition = (content.inline() ? "inline" : "attachment") + "; filename=\"" + content.fileName() + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, content.mediaType().toString())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(content.bytes());
    }

    public record LabelRequest(@NotBlank @Size(max = 120) String label) {
    }

    public record AssetResponse(Long id, String kind, String path, String name, String label, String mediaType, long size) {
        static AssetResponse of(Asset asset) {
            return new AssetResponse(asset.getId(), asset.getKind().name().toLowerCase(), asset.getStorageKey(),
                    asset.getOriginalName(), asset.displayName(), asset.getMediaType(), asset.getSizeBytes());
        }
    }
}
