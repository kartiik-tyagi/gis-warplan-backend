package com.gis.warplan.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.gis.warplan.config.WarplanProperties;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.UnavailableException;

/**
 * File-backed icon store. Every client supplied path is resolved against the configured root and
 * rejected when it would leave it.
 */
@Service
public class IconService {

    public static final long MAX_BYTES = 512 * 1024;

    private static final Logger log = LogManager.getLogger(IconService.class);
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._ -]{0,99}");
    private static final Map<String, MediaType> TYPES = Map.of(
            "svg", MediaType.valueOf("image/svg+xml"),
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "gif", MediaType.IMAGE_GIF,
            "webp", MediaType.valueOf("image/webp"));

    private static final Map<String, String> LEGACY_ICONS = Map.of(
            "circle.svg", svg("<circle cx=\"8\" cy=\"8\" r=\"6\" fill=\"#2f6f4e\"/>"),
            "square.svg", svg("<rect x=\"2\" y=\"2\" width=\"12\" height=\"12\" fill=\"#1f4e79\"/>"),
            "triangle.svg", svg("<polygon points=\"8,2 14,14 2,14\" fill=\"#a23b2a\"/>"));

    private static final Map<String, String> DEFAULT_ICONS = Map.of(
            "circle.svg", svg("<circle cx=\"8\" cy=\"8\" r=\"6\" fill=\"var(--wp-primary)\" data-wp-fill=\"primary\"/>"),
            "square.svg", svg("<rect x=\"2\" y=\"2\" width=\"12\" height=\"12\" fill=\"var(--wp-primary)\" data-wp-fill=\"primary\"/>"),
            "triangle.svg", svg("<polygon points=\"8,2 14,14 2,14\" fill=\"var(--wp-primary)\" data-wp-fill=\"primary\"/>"));

    private final AssetManagementService assets;
    private final String fallbackRoot;

    public IconService(WarplanProperties properties, AssetManagementService assets) {
        this.assets = assets;
        this.fallbackRoot = properties.getIcons().getRoot();
    }

    /** The current base icon folder, from the asset_management row (falling back to the configured root). */
    private Path root() {
        String configured = assets.path();
        String value = configured == null || configured.isBlank() ? fallbackRoot : configured;
        return Paths.get(value).toAbsolutePath().normalize();
    }

    /**
     * Ensures the base icon folder exists. A bad configured path must not return a 500 or abort startup,
     * so this surfaces a clean, actionable error that the Icons form shows.
     */
    private void ensureRoot(Path root) {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            log.warn("icon.root_unavailable path={} reason={}", root, exception.getMessage());
            throw new UnavailableException("The icon folder is not available: " + root
                    + ". Set a valid base icon folder in Icons administration.");
        }
    }

    public Path getRoot() {
        return root();
    }

    /** Writes the starter icons that are not on disk yet. Never fails startup: a bad path is logged and skipped. */
    public void seedDefaults() {
        Path root;
        try {
            root = root();
            ensureRoot(root);
        } catch (RuntimeException exception) {
            log.warn("icon.seed_skipped reason={}", exception.getMessage());
            return;
        }
        DEFAULT_ICONS.forEach((name, content) -> {
            Path target = root.resolve(name);
            try {
                if (Files.notExists(target)) {
                    Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                    log.info("Seeded icon {}", name);
                    return;
                }
                String legacy = LEGACY_ICONS.get(name);
                if (legacy != null && normalize(Files.readString(target)).equals(normalize(legacy))) {
                    Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    log.info("Updated seeded icon {} so it follows the active theme", name);
                }
            } catch (FileAlreadyExistsException ignored) {
                // created concurrently
            } catch (IOException exception) {
                log.warn("icon.seed_failed name={} reason={}", name, exception.getMessage());
            }
        });
    }

    public IconNode tree() {
        Path root = root();
        ensureRoot(root);
        return node(root, "", assets.labels());
    }

    public IconContent read(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BadRequestException("Icon path is required.");
        }
        Path root = root();
        Path file = resolveExisting(root, relativePath);
        if (!Files.isRegularFile(file)) {
            throw new BadRequestException("Icon path is not a file.");
        }
        MediaType type = mediaType(file.getFileName().toString());
        if (type == null) {
            throw new BadRequestException("Icon type is not supported.");
        }
        try {
            return new IconContent(Files.readAllBytes(file), type);
        } catch (IOException exception) {
            throw new IllegalStateException("Icon could not be read", exception);
        }
    }

    public String upload(String directory, String originalName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("Icon file is empty.");
        }
        if (content.length > MAX_BYTES) {
            throw new BadRequestException("Icon file is too large.");
        }
        String fileName = originalName == null ? "" : originalName.trim();
        if (!FILE_NAME.matcher(fileName).matches()) {
            throw new BadRequestException("Icon file name is not valid.");
        }
        MediaType type = mediaType(fileName);
        if (type == null) {
            throw new BadRequestException("Icon type is not supported.");
        }
        if (type.getSubtype().equals("svg+xml")) {
            content = drawnBytes(new String(content, StandardCharsets.UTF_8));
        }
        return writeNew(directory, fileName, content);
    }

    /** Stores a drawing as a new SVG. The name may omit {@code .svg}. */
    public StoredIcon draw(String directory, String name, String svg) {
        String fileName = svgFileName(name);
        byte[] content = drawnBytes(svg);
        String path = writeNew(directory == null ? "" : directory, fileName, content);
        log.info("icon.drawn path={} bytes={}", path, content.length);
        return new StoredIcon(path, content.length);
    }

    /** Replaces the bytes of an existing SVG. The file name stays the same. */
    public StoredIcon replaceDrawn(String relativePath, String svg) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BadRequestException("Icon path is required.");
        }
        String relative = relativePath.trim();
        if (!relative.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            throw new BadRequestException("Icon type is not supported.");
        }
        byte[] content = drawnBytes(svg);
        Path root = root();
        Path lexical = resolveLexically(root, relative);
        if (Files.isDirectory(lexical)) {
            throw new BadRequestException("Icon path is not a file.");
        }
        Path file = resolveExisting(root, relative);
        if (!Files.isRegularFile(file)) {
            throw new BadRequestException("Icon path is not a file.");
        }
        try {
            Files.write(file, content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Icon could not be stored", exception);
        }
        String path = relative(root, lexical);
        log.info("icon.revised path={} bytes={}", path, content.length);
        return new StoredIcon(path, content.length);
    }

    private String writeNew(String directory, String fileName, byte[] content) {
        Path root = root();
        Path directoryPath = resolveForWrite(root, directory);
        Path target = directoryPath.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new BadRequestException("Icon path is not valid.");
        }
        try {
            Files.createDirectories(directoryPath);
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException exception) {
            throw new ConflictException("An icon with that name already exists.");
        } catch (IOException exception) {
            throw new IllegalStateException("Icon could not be stored", exception);
        }
        return relative(root, target);
    }

    private static byte[] drawnBytes(String svg) {
        if (svg == null || svg.isBlank()) {
            throw new BadRequestException("Icon file is empty.");
        }
        if (svg.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BadRequestException("Icon file is too large.");
        }
        byte[] clean = SvgIconSanitizer.sanitize(svg).getBytes(StandardCharsets.UTF_8);
        if (clean.length == 0) {
            throw new BadRequestException("Icon file is empty.");
        }
        if (clean.length > MAX_BYTES) {
            throw new BadRequestException("Icon file is too large.");
        }
        return clean;
    }

    private static String svgFileName(String name) {
        String fileName = name == null ? "" : name.trim();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            fileName = fileName + ".svg";
        }
        if (!FILE_NAME.matcher(fileName).matches()) {
            throw new BadRequestException("Icon file name is not valid.");
        }
        return fileName;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private IconNode node(Path path, String relativePath, Map<String, String> labels) {
        boolean directory = Files.isDirectory(path);
        String name = relativePath.isEmpty() ? "" : path.getFileName().toString();
        if (!directory) {
            String label = labels.getOrDefault(relativePath, name);
            return new IconNode(name, relativePath, "file", label, List.of());
        }
        List<IconNode> children = new ArrayList<>();
        try (Stream<Path> stream = Files.list(path)) {
            stream.filter(child -> !Files.isSymbolicLink(child))
                    .filter(child -> Files.isDirectory(child) || mediaType(child.getFileName().toString()) != null)
                    .sorted(Comparator.comparing((Path child) -> !Files.isDirectory(child))
                            .thenComparing(child -> child.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(child -> {
                        String childName = child.getFileName().toString();
                        children.add(node(child, relativePath.isEmpty() ? childName : relativePath + "/" + childName, labels));
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("Icons could not be listed", exception);
        }
        return new IconNode(name, relativePath, "directory", name, children);
    }

    private Path resolveExisting(Path root, String relativePath) {
        Path candidate = resolveLexically(root, relativePath);
        if (Files.notExists(candidate)) {
            throw new BadRequestException("Icon was not found.");
        }
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(root.toRealPath())) {
                throw new BadRequestException("Icon path is not valid.");
            }
            return real;
        } catch (IOException exception) {
            throw new BadRequestException("Icon was not found.");
        }
    }

    private Path resolveForWrite(Path root, String directory) {
        Path candidate = resolveLexically(root, directory);
        Path existing = candidate;
        while (existing != null && Files.notExists(existing)) {
            existing = existing.getParent();
        }
        try {
            if (existing == null || !existing.toRealPath().startsWith(root.toRealPath())) {
                throw new BadRequestException("Icon path is not valid.");
            }
        } catch (IOException exception) {
            throw new BadRequestException("Icon path is not valid.");
        }
        return candidate;
    }

    private Path resolveLexically(Path root, String relativePath) {
        String value = relativePath == null ? "" : relativePath.trim();
        if (value.isEmpty()) {
            return root;
        }
        if (value.indexOf('\0') >= 0 || value.indexOf('\\') >= 0 || value.startsWith("/") || value.contains(":")) {
            throw new BadRequestException("Icon path is not valid.");
        }
        Path current = root;
        for (String segment : value.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.equals(".") || segment.equals("..") || !SEGMENT.matcher(segment).matches()) {
                throw new BadRequestException("Icon path is not valid.");
            }
            current = current.resolve(segment);
        }
        current = current.normalize();
        if (!current.startsWith(root)) {
            throw new BadRequestException("Icon path is not valid.");
        }
        return current;
    }

    private String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static MediaType mediaType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return TYPES.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String svg(String body) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\">"
                + body + "</svg>\n";
    }

    public record IconNode(String name, String path, String type, String label, List<IconNode> children) {
    }

    public record IconContent(byte[] bytes, MediaType mediaType) {
    }

    public record StoredIcon(String path, int size) {
    }
}
