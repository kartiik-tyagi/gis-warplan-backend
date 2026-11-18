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

import jakarta.annotation.PostConstruct;

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

    private static final Map<String, String> DEFAULT_ICONS = Map.of(
            "circle.svg", svg("<circle cx=\"8\" cy=\"8\" r=\"6\" fill=\"#2f6f4e\"/>"),
            "square.svg", svg("<rect x=\"2\" y=\"2\" width=\"12\" height=\"12\" fill=\"#1f4e79\"/>"),
            "triangle.svg", svg("<polygon points=\"8,2 14,14 2,14\" fill=\"#a23b2a\"/>"));

    private final Path root;

    public IconService(WarplanProperties properties) {
        this.root = Paths.get(properties.getIcons().getRoot()).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void ensureRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Icon root could not be created: " + root, exception);
        }
    }

    public Path getRoot() {
        return root;
    }

    /** Writes the starter icons that are not on disk yet. */
    public void seedDefaults() {
        ensureRoot();
        DEFAULT_ICONS.forEach((name, content) -> {
            Path target = root.resolve(name);
            if (Files.notExists(target)) {
                try {
                    Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                    log.info("Seeded icon {}", name);
                } catch (FileAlreadyExistsException ignored) {
                    // created concurrently
                } catch (IOException exception) {
                    throw new IllegalStateException("Icon could not be seeded: " + name, exception);
                }
            }
        });
    }

    public IconNode tree() {
        ensureRoot();
        return node(root, "");
    }

    public IconContent read(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BadRequestException("Icon path is required.");
        }
        Path file = resolveExisting(relativePath);
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
            rejectUnsafeSvg(content);
        }
        Path directoryPath = resolveForWrite(directory);
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
        return relative(target);
    }

    private IconNode node(Path path, String relativePath) {
        boolean directory = Files.isDirectory(path);
        String name = relativePath.isEmpty() ? "" : path.getFileName().toString();
        if (!directory) {
            return new IconNode(name, relativePath, "file", List.of());
        }
        List<IconNode> children = new ArrayList<>();
        try (Stream<Path> stream = Files.list(path)) {
            stream.filter(child -> !Files.isSymbolicLink(child))
                    .filter(child -> Files.isDirectory(child) || mediaType(child.getFileName().toString()) != null)
                    .sorted(Comparator.comparing((Path child) -> !Files.isDirectory(child))
                            .thenComparing(child -> child.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(child -> {
                        String childName = child.getFileName().toString();
                        children.add(node(child, relativePath.isEmpty() ? childName : relativePath + "/" + childName));
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("Icons could not be listed", exception);
        }
        return new IconNode(name, relativePath, "directory", children);
    }

    private Path resolveExisting(String relativePath) {
        Path candidate = resolveLexically(relativePath);
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

    private Path resolveForWrite(String directory) {
        Path candidate = resolveLexically(directory);
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

    private Path resolveLexically(String relativePath) {
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

    private String relative(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static MediaType mediaType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return TYPES.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static void rejectUnsafeSvg(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (!text.contains("<svg") || text.contains("<script") || text.contains("javascript:")
                || text.contains("<foreignobject") || text.contains("<!entity") || Pattern.compile("\\son\\w+\\s*=").matcher(text).find()) {
            throw new BadRequestException("SVG content is not allowed.");
        }
    }

    private static String svg(String body) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\">"
                + body + "</svg>\n";
    }

    public record IconNode(String name, String path, String type, List<IconNode> children) {
    }

    public record IconContent(byte[] bytes, MediaType mediaType) {
    }
}
