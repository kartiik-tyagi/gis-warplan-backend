package com.gis.warplan.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.ApplicationSlot;
import com.gis.warplan.domain.Asset;
import com.gis.warplan.domain.AssetKind;
import com.gis.warplan.domain.AssetManagement;
import com.gis.warplan.repository.AssetRepository;
import com.gis.warplan.repository.NodeTypeRepository;
import com.gis.warplan.repository.ScenarioRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.NotFoundException;
import com.gis.warplan.web.UnavailableException;

/**
 * One store for icons, pictures, documents, and the three application pictures.
 * Bytes live under the configurable base folder. A label is public.
 */
@Service
public class AssetService {

    public static final long DEFAULT_ICON_MAX = 512L * 1024L;
    public static final long DEFAULT_IMAGE_MAX = 10L * 1024L * 1024L;
    public static final long DEFAULT_DOCUMENT_MAX = 50L * 1024L * 1024L;
    public static final long CEILING = DEFAULT_DOCUMENT_MAX;

    private static final Logger log = LogManager.getLogger(AssetService.class);
    private static final Set<String> ICON_EXT = Set.of("svg", "png");
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final Set<String> DOCUMENT_EXT = Set.of("txt", "doc", "docx", "pdf");
    private static final Set<String> SKIP_DIRS = Set.of("icons", "images", "documents", "application");

    private final AssetRepository assets;
    private final AssetManagementService settings;
    private final NodeTypeRepository nodeTypes;
    private final SideRepository sides;
    private final ScenarioRepository scenarios;
    private final AccessService access;

    public AssetService(AssetRepository assets, AssetManagementService settings, NodeTypeRepository nodeTypes,
            SideRepository sides, ScenarioRepository scenarios, AccessService access) {
        this.assets = assets;
        this.settings = settings;
        this.nodeTypes = nodeTypes;
        this.sides = sides;
        this.scenarios = scenarios;
        this.access = access;
    }

    public record AssetContent(byte[] bytes, MediaType mediaType, String fileName, boolean inline) {
    }

    public record Limits(long iconMaxBytes, long imageMaxBytes, long documentMaxBytes) {
    }

    @Transactional(readOnly = true)
    public Limits limits() {
        AssetManagement row = settings.load();
        return new Limits(limit(row.getIconMaxBytes(), DEFAULT_ICON_MAX),
                limit(row.getImageMaxBytes(), DEFAULT_IMAGE_MAX),
                limit(row.getDocumentMaxBytes(), DEFAULT_DOCUMENT_MAX));
    }

    @Transactional(readOnly = true)
    public List<Asset> list(AssetKind kind) {
        if (kind == null || kind == AssetKind.APPLICATION) {
            throw new BadRequestException("Choose icons, images, or documents.");
        }
        return assets.findByKindOrderByOriginalNameAsc(kind);
    }

    @Transactional
    public Asset storeUpload(AssetKind kind, String originalName, byte[] content, String label) {
        if (kind == null || kind == AssetKind.APPLICATION) {
            throw new BadRequestException("Choose icons, images, or documents.");
        }
        Prepared prepared = prepare(kind, originalName, content, false);
        Asset saved = writeNew(kind, null, prepared, label);
        log.info("asset.stored id={} kind={} bytes={}", saved.getId(), kind, saved.getSizeBytes());
        return saved;
    }

    /** A drawing is always a new SVG icon. The original file is never overwritten. */
    @Transactional
    public Asset storeDrawing(String name, String svg, String label) {
        String fileName = name == null ? "" : name.trim();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            fileName = fileName + ".svg";
        }
        Prepared prepared = prepare(AssetKind.ICON, fileName, svg == null ? null : svg.getBytes(java.nio.charset.StandardCharsets.UTF_8), true);
        Asset saved = writeNew(AssetKind.ICON, null, prepared, label);
        log.info("asset.drawn id={} bytes={}", saved.getId(), saved.getSizeBytes());
        return saved;
    }

    @Transactional
    public Asset replaceApplication(ApplicationSlot slot, String originalName, byte[] content) {
        if (slot == null) {
            throw new BadRequestException("Choose a logo, favicon, or login picture.");
        }
        AssetKind check = slot == ApplicationSlot.LOGIN_BACKGROUND ? AssetKind.IMAGE : AssetKind.ICON;
        Set<String> allowed = slot == ApplicationSlot.LOGIN_BACKGROUND
                ? Set.of("svg", "png", "jpg", "jpeg", "webp")
                : Set.of("svg", "png");
        Prepared prepared = prepareAllowed(check, allowed, originalName, content, slot != ApplicationSlot.LOGIN_BACKGROUND);
        Asset existing = assets.findByKindAndSlot(AssetKind.APPLICATION, slot).orElse(null);
        if (existing == null) {
            Asset created = writeNew(AssetKind.APPLICATION, slot, prepared, null);
            log.info("asset.application_stored slot={} id={}", slot, created.getId());
            return created;
        }
        String key = storageKey(AssetKind.APPLICATION, existing.getId(), prepared.extension());
        Path file = resolveKey(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, prepared.bytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            if (!key.equals(existing.getStorageKey())) {
                Files.deleteIfExists(resolveKey(existing.getStorageKey()));
            }
        } catch (IOException exception) {
            log.error("asset.write_failed slot={} reason={}", slot, exception.getMessage());
            throw new UnavailableException("The asset folder could not be written.");
        }
        existing.setOriginalName(prepared.originalName());
        existing.setMediaType(prepared.mediaType().toString());
        existing.setSizeBytes(prepared.bytes().length);
        existing.setStorageKey(key);
        Asset saved = assets.save(existing);
        log.info("asset.application_replaced slot={} id={} bytes={}", slot, saved.getId(), saved.getSizeBytes());
        return saved;
    }

    @Transactional(readOnly = true)
    public AssetContent readByKey(String storageKey) {
        String key = requireKey(storageKey);
        Asset asset = assets.findByStorageKey(key).orElse(null);
        if (asset == null) {
            log.warn("asset.missing key={}", key);
            throw new NotFoundException("The file was not found.");
        }
        return readFile(asset);
    }

    @Transactional(readOnly = true)
    public AssetContent read(Long id) {
        Asset asset = assets.findById(id).orElse(null);
        if (asset == null) {
            log.warn("asset.missing id={}", id);
            throw new NotFoundException("The file was not found.");
        }
        return readFile(asset);
    }

    /** Public picture for the sign-in screen and the top bar. Null when the slot has not been stored. */
    @Transactional(readOnly = true)
    public AssetContent readApplication(ApplicationSlot slot) {
        if (slot == null) {
            return null;
        }
        Asset asset = assets.findByKindAndSlot(AssetKind.APPLICATION, slot).orElse(null);
        if (asset == null) {
            return null;
        }
        Path file = resolveKey(asset.getStorageKey());
        if (!Files.isRegularFile(file)) {
            log.warn("asset.application_file_missing slot={} key={}", slot, asset.getStorageKey());
            return null;
        }
        try {
            return new AssetContent(Files.readAllBytes(file), MediaType.parseMediaType(asset.getMediaType()),
                    asset.displayName(), true);
        } catch (IOException exception) {
            log.warn("asset.application_read_failed slot={} reason={}", slot, exception.getMessage());
            return null;
        }
    }

    public String applicationUrl(ApplicationSlot slot) {
        AssetContent content = readApplication(slot);
        if (content == null) {
            return null;
        }
        return "/api/v1/assets/application/" + slot.key() + "/content";
    }

    @Transactional
    public Asset setLabelByKey(String storageKey, String label) {
        String key = requireKey(storageKey);
        Asset asset = assets.findByStorageKey(key).orElse(null);
        if (asset == null) {
            log.warn("asset.missing key={}", key);
            throw new NotFoundException("The file was not found.");
        }
        return setLabel(asset.getId(), label);
    }

    @Transactional
    public Asset setLabel(Long id, String label) {
        String value = requireLabel(label);
        Asset asset = require(id);
        asset.setLabel(value);
        Asset saved = assets.save(asset);
        log.info("asset.label_set id={}", id);
        return saved;
    }

    @Transactional
    public Asset clearLabelByKey(String storageKey) {
        String key = requireKey(storageKey);
        Asset asset = assets.findByStorageKey(key).orElse(null);
        if (asset == null) {
            log.warn("asset.missing key={}", key);
            throw new NotFoundException("The file was not found.");
        }
        return clearLabel(asset.getId());
    }

    @Transactional
    public Asset clearLabel(Long id) {
        Asset asset = require(id);
        asset.setLabel(null);
        Asset saved = assets.save(asset);
        log.info("asset.label_cleared id={}", id);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Asset asset = require(id);
        if (asset.getKind() != AssetKind.APPLICATION) {
            if (nodeTypes.existsByIconPath(asset.getStorageKey()) || sides.existsByIcon(asset.getStorageKey())) {
                log.warn("asset.delete_rejected id={} reason=in_use", id);
                throw new ConflictException("This file is still used by a node type or a side.");
            }
            if (scenarios.existsByAttachmentAssetId(id)) {
                log.warn("asset.delete_rejected id={} reason=scenario", id);
                throw new ConflictException("This file is still attached to a scenario.");
            }
        }
        deleteFile(asset);
        assets.delete(asset);
        log.info("asset.deleted id={} kind={}", id, asset.getKind());
    }

    /** Removes a scenario's file. Missing rows are ignored so a delete can finish. */
    @Transactional
    public void deleteQuietly(Long id) {
        if (id == null) {
            return;
        }
        Asset asset = assets.findById(id).orElse(null);
        if (asset == null) {
            return;
        }
        deleteFile(asset);
        assets.delete(asset);
        log.info("asset.deleted id={} kind={} quiet=true", id, asset.getKind());
    }

    /** Registers icon files already sitting in the base folder, and copies seed pictures into empty slots. */
    @Transactional
    public void importLooseIcons(Map<String, String> publicLabels) {
        Path root = root();
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(file -> registerLoose(root, file, publicLabels));
        } catch (IOException exception) {
            log.warn("asset.import_skipped reason={}", exception.getMessage());
        }
    }

    @Transactional
    public Asset ensureSeedSlot(ApplicationSlot slot, String classpath, String fileName) {
        if (assets.findByKindAndSlot(AssetKind.APPLICATION, slot).isPresent()) {
            return assets.findByKindAndSlot(AssetKind.APPLICATION, slot).orElseThrow();
        }
        try (InputStream in = AssetService.class.getResourceAsStream(classpath)) {
            if (in == null) {
                log.warn("asset.seed_missing slot={} resource={}", slot, classpath);
                return null;
            }
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            return replaceApplication(slot, fileName, bytes);
        } catch (IOException exception) {
            log.warn("asset.seed_failed slot={} reason={}", slot, exception.getMessage());
            return null;
        } catch (RuntimeException exception) {
            log.warn("asset.seed_failed slot={} reason={}", slot, exception.getMessage());
            return null;
        }
    }

    private void registerLoose(Path root, Path file, Map<String, String> publicLabels) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        if (relative.isBlank() || assets.existsByStorageKey(relative)) {
            return;
        }
        String first = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : "";
        if (SKIP_DIRS.contains(first)) {
            return;
        }
        String extension = extensionOf(file.getFileName().toString());
        if (!ICON_EXT.contains(extension)) {
            return;
        }
        Asset asset = new Asset();
        asset.setKind(AssetKind.ICON);
        asset.setStorageKey(relative);
        asset.setOriginalName(file.getFileName().toString());
        asset.setMediaType(mediaType(extension).toString());
        try {
            asset.setSizeBytes(Files.size(file));
        } catch (IOException exception) {
            asset.setSizeBytes(0L);
        }
        String label = publicLabels == null ? null : publicLabels.get(relative);
        if (label != null && !label.isBlank() && !label.equals(asset.getOriginalName())) {
            asset.setLabel(trimLabel(label));
        }
        assets.save(asset);
        log.info("asset.imported key={}", relative);
    }

    private Asset writeNew(AssetKind kind, ApplicationSlot slot, Prepared prepared, String label) {
        Asset asset = new Asset();
        asset.setKind(kind == AssetKind.APPLICATION ? AssetKind.APPLICATION : kind);
        asset.setSlot(slot);
        asset.setStorageKey("pending");
        asset.setOriginalName(prepared.originalName());
        asset.setMediaType(prepared.mediaType().toString());
        asset.setSizeBytes(prepared.bytes().length);
        asset.setCreatedBy(access.currentPrincipal().map(principal -> principal.userId()).orElse(null));
        if (label != null && !label.isBlank()) {
            asset.setLabel(requireLabel(label));
        }
        Asset saved = assets.saveAndFlush(asset);
        String key = storageKey(saved.getKind(), saved.getId(), prepared.extension());
        Path target = resolveKey(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, prepared.bytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            assets.delete(saved);
            log.error("asset.write_failed key={} reason={}", key, exception.getMessage());
            throw new UnavailableException("The asset folder could not be written.");
        }
        saved.setStorageKey(key);
        return assets.save(saved);
    }

    private Prepared prepare(AssetKind kind, String originalName, byte[] content, boolean svgSanitize) {
        Set<String> allowed = switch (kind) {
            case ICON -> ICON_EXT;
            case IMAGE -> IMAGE_EXT;
            case DOCUMENT -> DOCUMENT_EXT;
            case APPLICATION -> Set.of();
        };
        return prepareAllowed(kind, allowed, originalName, content, svgSanitize);
    }

    private Prepared prepareAllowed(AssetKind limitKind, Set<String> allowed, String originalName, byte[] content,
            boolean svgSanitize) {
        if (content == null || content.length == 0) {
            log.warn("asset.rejected reason=empty");
            throw new BadRequestException("The file is empty.");
        }
        String safeName = sanitizeFileName(originalName);
        String extension = extensionOf(safeName);
        if (!allowed.contains(extension)) {
            log.warn("asset.rejected reason=type extension={}", extension);
            throw new BadRequestException("That file type is not allowed here.");
        }
        long max = maxFor(limitKind);
        if (content.length > max) {
            log.warn("asset.rejected reason=size bytes={} max={}", content.length, max);
            throw new BadRequestException("The file is larger than " + humanSize(max) + ".");
        }
        byte[] bytes = content;
        if (svgSanitize && "svg".equals(extension)) {
            String cleaned = SvgIconSanitizer.sanitize(new String(content, java.nio.charset.StandardCharsets.UTF_8));
            bytes = cleaned.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length > max) {
                throw new BadRequestException("The file is larger than " + humanSize(max) + ".");
            }
        }
        return new Prepared(safeName, extension, mediaType(extension), bytes);
    }

    private long maxFor(AssetKind kind) {
        Limits current = limits();
        return switch (kind) {
            case ICON, APPLICATION -> current.iconMaxBytes();
            case IMAGE -> current.imageMaxBytes();
            case DOCUMENT -> current.documentMaxBytes();
        };
    }

    private AssetContent readFile(Asset asset) {
        Path file = resolveKey(asset.getStorageKey());
        if (!Files.isRegularFile(file)) {
            log.warn("asset.file_missing id={} key={}", asset.getId(), asset.getStorageKey());
            throw new NotFoundException("The file was not found.");
        }
        try {
            String extension = extensionOf(asset.getOriginalName());
            boolean inline = Set.of("svg", "png", "jpg", "jpeg", "gif", "webp", "txt", "pdf").contains(extension);
            return new AssetContent(Files.readAllBytes(file), MediaType.parseMediaType(asset.getMediaType()),
                    asset.displayName(), inline);
        } catch (IOException exception) {
            log.error("asset.read_failed id={} reason={}", asset.getId(), exception.getMessage());
            throw new UnavailableException("The file could not be read.");
        }
    }

    private void deleteFile(Asset asset) {
        try {
            Files.deleteIfExists(resolveKey(asset.getStorageKey()));
        } catch (IOException exception) {
            log.warn("asset.file_delete_failed id={} reason={}", asset.getId(), exception.getMessage());
        }
    }

    private Asset require(Long id) {
        if (id == null) {
            throw new BadRequestException("The file is required.");
        }
        return assets.findById(id).orElseThrow(() -> {
            log.warn("asset.missing id={}", id);
            return new NotFoundException("The file was not found.");
        });
    }

    private Path root() {
        Path path = Paths.get(settings.path()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            log.error("asset.root_unavailable path={} reason={}", path, exception.getMessage());
            throw new UnavailableException("The asset folder is not available.");
        }
        return path;
    }

    private Path resolveKey(String storageKey) {
        String key = requireKey(storageKey);
        Path root = root();
        Path resolved = root;
        for (String segment : key.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                log.warn("asset.rejected reason=path key={}", key);
                throw new BadRequestException("The file path is not valid.");
            }
            resolved = resolved.resolve(segment);
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(root)) {
            log.warn("asset.rejected reason=escape key={}", key);
            throw new BadRequestException("The file path is not valid.");
        }
        return resolved;
    }

    private static String requireKey(String storageKey) {
        String key = storageKey == null ? "" : storageKey.trim().replace('\\', '/');
        if (key.isEmpty() || key.indexOf('\0') >= 0 || key.startsWith("/") || key.contains(":") || key.contains("..")) {
            log.warn("asset.rejected reason=path");
            throw new BadRequestException("The file path is not valid.");
        }
        return key;
    }

    private static String storageKey(AssetKind kind, Long id, String extension) {
        return kind.folder() + "/" + id + "." + extension;
    }

    private static String requireLabel(String label) {
        String value = trimLabel(label);
        if (value.isEmpty()) {
            throw new BadRequestException("A label is required.");
        }
        return value;
    }

    private static String trimLabel(String label) {
        String value = label == null ? "" : label.trim();
        if (value.length() > 120) {
            throw new BadRequestException("The label is too long.");
        }
        return value;
    }

    private static String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            throw new BadRequestException("The file name is missing.");
        }
        String name = Paths.get(original).getFileName().toString().trim();
        name = name.replaceAll("[\\\\/]+", "");
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new BadRequestException("The file name is not valid.");
        }
        if (name.length() > 180) {
            String extension = extensionOf(name);
            name = name.substring(0, 160) + (extension.isEmpty() ? "" : "." + extension);
        }
        return name;
    }

    static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static AssetKind kindForScenario(String fileName) {
        String extension = extensionOf(fileName == null ? "" : fileName);
        if (IMAGE_EXT.contains(extension)) {
            return AssetKind.IMAGE;
        }
        if (DOCUMENT_EXT.contains(extension)) {
            return AssetKind.DOCUMENT;
        }
        return null;
    }

    private static MediaType mediaType(String extension) {
        return switch (extension) {
            case "svg" -> MediaType.valueOf("image/svg+xml");
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.valueOf("image/webp");
            case "txt" -> MediaType.TEXT_PLAIN;
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "doc" -> MediaType.valueOf("application/msword");
            case "docx" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private static long limit(Long stored, long fallback) {
        if (stored == null || stored < 1) {
            return fallback;
        }
        return Math.min(stored, CEILING);
    }

    public static String humanSize(long bytes) {
        if (bytes % (1024L * 1024L) == 0) {
            return (bytes / (1024L * 1024L)) + " MB";
        }
        if (bytes % 1024L == 0) {
            return (bytes / 1024L) + " KB";
        }
        return bytes + " bytes";
    }

    /** Flattens the old per-user label map. The lowest user id wins when several people named one file. */
    public static Map<String, String> flattenLabels(Map<String, Object> raw) {
        Map<String, String> flat = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return flat;
        }
        boolean nested = raw.values().stream().anyMatch(value -> value instanceof Map<?, ?>);
        if (!nested) {
            raw.forEach((key, value) -> {
                if (key != null && value != null && !(value instanceof Map<?, ?>)) {
                    String label = String.valueOf(value).trim();
                    if (!label.isEmpty()) {
                        flat.put(String.valueOf(key), label);
                    }
                }
            });
            return flat;
        }
        Map<Long, Map<String, String>> byUser = new TreeMap<>();
        raw.forEach((userKey, value) -> {
            if (!(value instanceof Map<?, ?> inner)) {
                return;
            }
            long userId;
            try {
                userId = Long.parseLong(String.valueOf(userKey));
            } catch (NumberFormatException exception) {
                return;
            }
            Map<String, String> labels = new LinkedHashMap<>();
            inner.forEach((path, label) -> {
                if (path != null && label != null) {
                    String text = String.valueOf(label).trim();
                    if (!text.isEmpty()) {
                        labels.put(String.valueOf(path), text);
                    }
                }
            });
            byUser.put(userId, labels);
        });
        byUser.values().forEach(labels -> labels.forEach(flat::putIfAbsent));
        return flat;
    }

    private record Prepared(String originalName, String extension, MediaType mediaType, byte[] bytes) {
    }

    /** Used by tests that need a stable ordering of imported keys. */
    public List<String> importedKeys() {
        List<String> keys = new ArrayList<>();
        assets.findAll().forEach(asset -> keys.add(asset.getStorageKey()));
        keys.sort(Comparator.naturalOrder());
        return keys;
    }
}
