package com.gis.warplan.web;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.AssetManagementService;
import com.gis.warplan.service.AuditService;
import com.gis.warplan.service.IconService;
import com.gis.warplan.service.IconService.IconContent;
import com.gis.warplan.service.IconService.IconNode;
import com.gis.warplan.service.IconService.StoredIcon;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/icons")
public class IconController {

    private final IconService icons;
    private final AssetManagementService assets;
    private final AccessService access;
    private final AuditService audit;

    public IconController(IconService icons, AssetManagementService assets, AccessService access, AuditService audit) {
        this.icons = icons;
        this.assets = assets;
        this.access = access;
        this.audit = audit;
    }

    @GetMapping
    public IconNode tree() {
        return icons.tree();
    }

    @GetMapping("/content")
    public ResponseEntity<byte[]> content(@RequestParam("path") String path) {
        IconContent content = icons.read(path);
        return ResponseEntity.ok()
                .contentType(content.mediaType())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(content.bytes());
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "path", required = false, defaultValue = "") String directory) throws IOException {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        String stored = icons.upload(directory, file.getOriginalFilename(), file.getBytes());
        audit.record("UPLOAD", "Icon", null, "Icon uploaded " + stored, Map.of("path", stored));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("path", stored, "size", file.getSize()));
    }

    @PostMapping(value = "/draw", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> draw(@Valid @RequestBody DrawRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        StoredIcon stored = icons.draw(request.directory(), request.name(), request.svg());
        rememberLabel(stored.path(), request.label());
        audit.record("DRAW", "Icon", null, "Icon drawn " + stored.path(), Map.of("path", stored.path()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("path", stored.path(), "size", stored.size()));
    }

    @PutMapping(value = "/draw", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> revise(@Valid @RequestBody ReviseRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_ICONS);
        StoredIcon stored = icons.replaceDrawn(request.path(), request.svg());
        rememberLabel(stored.path(), request.label());
        audit.record("REVISE", "Icon", null, "Icon revised " + stored.path(), Map.of("path", stored.path()));
        return ResponseEntity.ok(Map.of("path", stored.path(), "size", stored.size()));
    }

    private void rememberLabel(String path, String label) {
        if (label != null && !label.isBlank()) {
            assets.setLabel(path, label);
        }
    }

    public record DrawRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 300) String directory,
            @Size(max = 120) String label,
            @NotBlank @Size(max = 524288) String svg) {
    }

    public record ReviseRequest(
            @NotBlank @Size(max = 300) String path,
            @Size(max = 120) String label,
            @NotBlank @Size(max = 524288) String svg) {
    }
}
