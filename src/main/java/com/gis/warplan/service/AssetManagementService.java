package com.gis.warplan.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.config.WarplanProperties;
import com.gis.warplan.domain.AssetManagement;
import com.gis.warplan.repository.AssetManagementRepository;
import com.gis.warplan.web.BadRequestException;

/**
 * The single {@code asset_management} row: the global base icon folder and the per-user icon labels.
 * Labels are keyed by user id, so each user only ever sees and edits their own labels. The row is
 * created on first use with the configured default path. {@code IconService} depends on this for the
 * folder and the current user's labels; this service never depends on {@code IconService} (no cycle).
 */
@Service
public class AssetManagementService {

    private static final Logger log = LogManager.getLogger(AssetManagementService.class);
    private static final int MAX_PATH = 500;
    private static final int MAX_LABEL = 120;

    private final AssetManagementRepository repository;
    private final WarplanProperties properties;
    private final AccessService access;
    private final AuditService audit;

    public AssetManagementService(AssetManagementRepository repository, WarplanProperties properties,
            AccessService access, AuditService audit) {
        this.repository = repository;
        this.properties = properties;
        this.access = access;
        this.audit = audit;
    }

    /** The configured fallback folder when no row exists yet or the stored path is blank. */
    public String defaultPath() {
        return properties.getIcons().getRoot();
    }

    /** Reads (creating on first use) the singleton row. */
    @Transactional
    public AssetManagement load() {
        return repository.findFirstByOrderByIdAsc().orElseGet(this::create);
    }

    /** The singleton row under a write lock, for read-modify-write of the shared labels/path. */
    @Transactional
    public AssetManagement loadForUpdate() {
        return repository.findForUpdate().stream().findFirst().orElseGet(this::create);
    }

    private AssetManagement create() {
        AssetManagement created = new AssetManagement();
        created.setPath(defaultPath());
        created.setLabels(new LinkedHashMap<>());
        created.setLastUpdated(Instant.now());
        AssetManagement saved = repository.save(created);
        log.info("asset_management.created path={}", saved.getPath());
        return saved;
    }

    /** The base icon folder, never blank. */
    @Transactional
    public String path() {
        String stored = load().getPath();
        return stored == null || stored.isBlank() ? defaultPath() : stored;
    }

    /** The signed-in user's icon labels (empty when nobody is signed in). */
    @Transactional
    public Map<String, String> labels() {
        String userKey = access.currentPrincipal().map(principal -> String.valueOf(principal.userId())).orElse(null);
        if (userKey == null) {
            return Map.of();
        }
        return labelsForUser(load().getLabels(), userKey);
    }

    /** Changes the base icon folder. The folder must exist or be creatable and writable, or the change is rejected. */
    @Transactional
    public AssetManagement updatePath(String rawPath) {
        String path = normalizePath(rawPath);
        verifyUsable(path);
        AssetManagement row = loadForUpdate();
        row.setPath(path);
        stamp(row);
        AssetManagement saved = repository.save(row);
        log.info("asset_management.path_updated path={}", path);
        audit.record("UPDATE", "AssetManagement", saved.getId(), "Icon base path set to " + path,
                Map.of("path", path));
        return saved;
    }

    /** Sets the signed-in user's label for one icon. The label is required and cannot be blank. */
    @Transactional
    public AssetManagement setLabel(String iconPath, String label) {
        String key = requireIconPath(iconPath);
        String value = requireLabel(label);
        String userKey = requireUserKey();
        AssetManagement row = loadForUpdate();
        Map<String, Object> labels = mutableLabels(row);
        Map<String, String> mine = labelsForUser(labels, userKey);
        mine.put(key, value);
        labels.put(userKey, new LinkedHashMap<>(mine));
        row.setLabels(labels);
        stamp(row);
        AssetManagement saved = repository.save(row);
        log.info("asset_management.label_set icon={} userId={}", key, userKey);
        audit.record("UPDATE", "AssetManagement", saved.getId(), "Icon label set for " + key,
                Map.of("icon", key, "label", value));
        return saved;
    }

    /** Clears the signed-in user's label for one icon so it falls back to the file name. */
    @Transactional
    public AssetManagement removeLabel(String iconPath) {
        String key = requireIconPath(iconPath);
        String userKey = requireUserKey();
        AssetManagement row = loadForUpdate();
        Map<String, Object> labels = mutableLabels(row);
        Map<String, String> mine = labelsForUser(labels, userKey);
        if (mine.remove(key) != null) {
            if (mine.isEmpty()) {
                labels.remove(userKey);
            } else {
                labels.put(userKey, new LinkedHashMap<>(mine));
            }
            row.setLabels(labels);
            stamp(row);
            repository.save(row);
            log.info("asset_management.label_cleared icon={} userId={}", key, userKey);
            audit.record("UPDATE", "AssetManagement", row.getId(), "Icon label cleared for " + key,
                    Map.of("icon", key));
        }
        return row;
    }

    /** One user's flat {iconPath -> label} map, read leniently from the nested/legacy structure. */
    public static Map<String, String> labelsForUser(Map<String, Object> labels, String userKey) {
        Map<String, String> result = new LinkedHashMap<>();
        if (labels != null && userKey != null && labels.get(userKey) instanceof Map<?, ?> inner) {
            inner.forEach((iconPath, label) -> {
                if (iconPath != null && label != null) {
                    result.put(String.valueOf(iconPath), String.valueOf(label));
                }
            });
        }
        return result;
    }

    /** A writable copy of the per-user label structure, dropping any legacy flat (non-map) entries. */
    private static Map<String, Object> mutableLabels(AssetManagement row) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (row.getLabels() != null) {
            row.getLabels().forEach((key, value) -> {
                if (value instanceof Map<?, ?>) {
                    clean.put(key, value);
                }
            });
        }
        return clean;
    }

    private void stamp(AssetManagement row) {
        row.setLastUpdated(Instant.now());
        row.setUpdatedBy(access.currentPrincipal().map(principal -> principal.userId()).orElse(null));
    }

    private String requireUserKey() {
        return String.valueOf(access.requirePrincipal().userId());
    }

    /** Format check only. A relative or absolute folder is allowed; {@link #verifyUsable} checks it works. */
    private static String normalizePath(String rawPath) {
        String value = rawPath == null ? "" : rawPath.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            throw new BadRequestException("The base icon folder is required.");
        }
        if (value.length() > MAX_PATH) {
            throw new BadRequestException("The base icon folder is too long (max " + MAX_PATH + " characters).");
        }
        if (value.indexOf('\0') >= 0) {
            throw new BadRequestException("The base icon folder is not a valid path.");
        }
        return value;
    }

    /** Confirms the folder exists or can be created and is a writable directory; rejects it with a clear note if not. */
    private static void verifyUsable(String path) {
        Path resolved;
        try {
            resolved = Paths.get(path).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            throw new BadRequestException("The base icon folder is not a valid path.");
        }
        try {
            Files.createDirectories(resolved);
        } catch (IOException exception) {
            throw new BadRequestException("The base icon folder could not be created: " + resolved
                    + " (" + exception.getMessage() + ").");
        }
        if (!Files.isDirectory(resolved)) {
            throw new BadRequestException("The base icon folder is not a directory: " + resolved + ".");
        }
        if (!Files.isWritable(resolved)) {
            throw new BadRequestException("The base icon folder is not writable: " + resolved + ".");
        }
    }

    private static String requireIconPath(String iconPath) {
        String value = iconPath == null ? "" : iconPath.trim();
        if (value.isEmpty()) {
            throw new BadRequestException("The icon path is required.");
        }
        return value;
    }

    private static String requireLabel(String label) {
        String value = label == null ? "" : label.trim();
        if (value.isEmpty()) {
            throw new BadRequestException("The label is required.");
        }
        if (value.length() > MAX_LABEL) {
            throw new BadRequestException("The label is too long.");
        }
        return value;
    }
}
