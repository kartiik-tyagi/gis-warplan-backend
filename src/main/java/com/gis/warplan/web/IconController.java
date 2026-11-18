package com.gis.warplan.web;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.AuditService;
import com.gis.warplan.service.IconService;
import com.gis.warplan.service.IconService.IconContent;
import com.gis.warplan.service.IconService.IconNode;

@RestController
@RequestMapping("/api/v1/icons")
public class IconController {

    private final IconService icons;
    private final AccessService access;
    private final AuditService audit;

    public IconController(IconService icons, AccessService access, AuditService audit) {
        this.icons = icons;
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
}
