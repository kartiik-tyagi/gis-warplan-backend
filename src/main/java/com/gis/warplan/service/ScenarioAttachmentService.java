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
import com.gis.warplan.domain.Asset;
import com.gis.warplan.domain.AssetKind;
import com.gis.warplan.domain.Scenario;
import com.gis.warplan.repository.ScenarioRepository;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.NotFoundException;

import jakarta.annotation.PostConstruct;

/**
 * One attachment per scenario, stored as an asset. The display name stays on the scenario.
 */
@Service
public class ScenarioAttachmentService {

    public static final long MAX_BYTES = 10 * 1024 * 1024;

    private static final Logger log = LogManager.getLogger(ScenarioAttachmentService.class);
    private static final Set<String> ALLOWED = Set.of("txt", "doc", "docx", "pdf", "jpg", "jpeg");

    private final Path root;
    private final ScenarioRepository scenarios;
    private final AssetService assets;

    public ScenarioAttachmentService(WarplanProperties properties, ScenarioRepository scenarios, AssetService assets) {
        this.root = Paths.get(properties.getAttachments().getRoot()).toAbsolutePath().normalize();
        this.scenarios = scenarios;
        this.assets = assets;
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
            log.warn("scenario.attachment_rejected id={} reason=empty", scenarioId);
            throw new BadRequestException("Choose a file to upload.");
        }
        String original = file.getOriginalFilename();
        AssetKind kind = AssetService.kindForScenario(original);
        if (kind == null) {
            log.warn("scenario.attachment_rejected id={} reason=type", scenarioId);
            throw new BadRequestException("Allowed types: txt, doc, docx, pdf, jpg, png, gif, webp.");
        }
        Scenario scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Scenario not found."));
        byte[] bytes = file.getBytes();
        Asset stored = assets.storeUpload(kind, original, bytes, null);
        if (scenario.getAttachmentAssetId() != null) {
            assets.deleteQuietly(scenario.getAttachmentAssetId());
        }
        scenario.setAttachmentAssetId(stored.getId());
        scenario.setAttachedFile(stored.displayName());
        scenarios.save(scenario);
        clearDirectory(scenarioDir(scenarioId));
        log.info("scenario.attachment.stored id={} assetId={} bytes={}", scenarioId, stored.getId(), stored.getSizeBytes());
        return stored.displayName();
    }

    public AttachmentContent read(Long scenarioId) {
        Scenario scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Scenario not found."));
        if (scenario.getAttachmentAssetId() == null) {
            log.warn("scenario.attachment_missing id={}", scenarioId);
            throw new NotFoundException("This scenario has no attachment.");
        }
        AssetService.AssetContent content = assets.read(scenario.getAttachmentAssetId());
        return new AttachmentContent(content.bytes(), content.mediaType(), content.fileName(), content.inline());
    }

    public void clear(Long scenarioId) {
        Scenario scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Scenario not found."));
        assets.deleteQuietly(scenario.getAttachmentAssetId());
        scenario.setAttachmentAssetId(null);
        scenario.setAttachedFile(null);
        scenarios.save(scenario);
        clearDirectory(scenarioDir(scenarioId));
        log.info("scenario.attachment.cleared id={}", scenarioId);
    }

    /** Removes the attached asset when a scenario is deleted. */
    public void purge(Long scenarioId) {
        Scenario scenario = scenarios.findById(scenarioId).orElse(null);
        if (scenario != null) {
            assets.deleteQuietly(scenario.getAttachmentAssetId());
            scenario.setAttachmentAssetId(null);
            scenario.setAttachedFile(null);
        }
        clearDirectory(scenarioDir(scenarioId));
        try {
            Files.deleteIfExists(scenarioDir(scenarioId));
        } catch (IOException exception) {
            log.warn("scenario.attachment_purge_failed id={} reason={}", scenarioId, exception.getMessage());
        }
    }

    /** Copies attachments that still live in the old per-scenario folder into the asset store. */
    public void importLegacy() {
        for (Scenario scenario : scenarios.findByAttachedFileIsNotNullAndAttachmentAssetIdIsNull()) {
            if (scenario.getAttachedFile() == null || scenario.getAttachedFile().isBlank()) {
                continue;
            }
            Path file;
            try {
                file = scenarioDir(scenario.getId()).resolve(sanitizeFileName(scenario.getAttachedFile())).normalize();
            } catch (RuntimeException exception) {
                log.warn("scenario.attachment_import_skipped id={} reason={}", scenario.getId(), exception.getMessage());
                continue;
            }
            if (!file.startsWith(scenarioDir(scenario.getId())) || !Files.isRegularFile(file)) {
                log.warn("scenario.attachment_import_missing id={}", scenario.getId());
                continue;
            }
            try {
                byte[] bytes = Files.readAllBytes(file);
                AssetKind kind = AssetService.kindForScenario(scenario.getAttachedFile());
                if (kind == null) {
                    log.warn("scenario.attachment_import_skipped id={} reason=type", scenario.getId());
                    continue;
                }
                Asset stored = assets.storeUpload(kind, scenario.getAttachedFile(), bytes, null);
                scenario.setAttachmentAssetId(stored.getId());
                scenario.setAttachedFile(stored.displayName());
                scenarios.save(scenario);
                Files.deleteIfExists(file);
                log.info("scenario.attachment_imported id={} assetId={}", scenario.getId(), stored.getId());
            } catch (RuntimeException | IOException exception) {
                log.warn("scenario.attachment_import_failed id={} reason={}", scenario.getId(), exception.getMessage());
            }
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
