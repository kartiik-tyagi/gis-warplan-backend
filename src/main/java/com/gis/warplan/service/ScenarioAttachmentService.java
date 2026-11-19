package com.gis.warplan.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.gis.warplan.config.WarplanProperties;
import com.gis.warplan.domain.Scenario;
import com.gis.warplan.repository.ScenarioRepository;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.NotFoundException;

import jakarta.annotation.PostConstruct;

/**
 * Stores one optional attachment per scenario under {@code warplan.attachments.root}/{scenarioId}/.
 * {@link Scenario#getAttachedFile()} holds the original file name for display.
 */
@Service
public class ScenarioAttachmentService {

    public static final long MAX_BYTES = 10 * 1024 * 1024;

    private static final Logger log = LogManager.getLogger(ScenarioAttachmentService.class);
    private static final Set<String> ALLOWED = Set.of("txt", "doc", "docx", "pdf", "jpg", "jpeg");

    private final Path root;
    private final ScenarioRepository scenarios;

    public ScenarioAttachmentService(WarplanProperties properties, ScenarioRepository scenarios) {
        this.root = Paths.get(properties.getAttachments().getRoot()).toAbsolutePath().normalize();
        this.scenarios = scenarios;
    }

    @PostConstruct
    public void ensureRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Scenario attachment root could not be created: " + root, exception);
        }
    }

    public record AttachmentContent(byte[] bytes, MediaType mediaType, String fileName, boolean inline) {
    }

    public String store(Long scenarioId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a file to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("The attachment is too large (max 10 MB).");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new BadRequestException("The file name is missing.");
        }
        String safeName = sanitizeFileName(original);
        String extension = extensionOf(safeName);
        if (!ALLOWED.contains(extension)) {
            throw new BadRequestException("Allowed types: txt, doc, docx, pdf, jpg.");
        }
        Scenario scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Scenario not found."));

        Path directory = scenarioDir(scenarioId);
        Files.createDirectories(directory);
        clearDirectory(directory);
        Path target = directory.resolve(safeName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        scenario.setAttachedFile(safeName);
        scenarios.save(scenario);
        log.info("scenario.attachment.stored id={} file={} bytes={}", scenarioId, safeName, file.getSize());
        return safeName;
    }

    public AttachmentContent read(Long scenarioId) {
        Scenario scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Scenario not found."));
        String name = scenario.getAttachedFile();
        if (name == null || name.isBlank()) {
            throw new NotFoundException("This scenario has no attachment.");
        }
        Path file = scenarioDir(scenarioId).resolve(sanitizeFileName(name)).normalize();
        if (!file.startsWith(scenarioDir(scenarioId)) || !Files.isRegularFile(file)) {
            throw new NotFoundException("This scenario has no attachment.");
        }
        try {
            String extension = extensionOf(name);
            MediaType type = mediaType(extension);
            boolean inline = Set.of("txt", "pdf", "jpg", "jpeg").contains(extension);
            return new AttachmentContent(Files.readAllBytes(file), type, name, inline);
        } catch (IOException exception) {
            throw new IllegalStateException("Attachment could not be read", exception);
        }
    }

    public void clear(Long scenarioId) {
        Scenario scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Scenario not found."));
        Path directory = scenarioDir(scenarioId);
        clearDirectory(directory);
        scenario.setAttachedFile(null);
        scenarios.save(scenario);
        log.info("scenario.attachment.cleared id={}", scenarioId);
    }

    /** Removes on-disk files when a scenario is deleted. */
    public void purge(Long scenarioId) {
        clearDirectory(scenarioDir(scenarioId));
        try {
            Files.deleteIfExists(scenarioDir(scenarioId));
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private Path scenarioDir(Long scenarioId) {
        return root.resolve(String.valueOf(scenarioId)).normalize();
    }

    private static void clearDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(directory))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best-effort
                        }
                    });
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static String sanitizeFileName(String original) {
        String name = Paths.get(original).getFileName().toString().trim();
        name = name.replaceAll("[\\\\/]+", "");
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new BadRequestException("The file name is not valid.");
        }
        if (name.length() > 160) {
            String extension = extensionOf(name);
            String base = name.substring(0, Math.min(140, name.length()));
            name = extension.isEmpty() ? base : base + "." + extension;
        }
        return name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static MediaType mediaType(String extension) {
        return switch (extension) {
            case "txt" -> MediaType.TEXT_PLAIN;
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "doc" -> MediaType.valueOf("application/msword");
            case "docx" -> MediaType.valueOf(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
