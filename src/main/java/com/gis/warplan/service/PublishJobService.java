package com.gis.warplan.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.gis.warplan.config.WarplanProperties;
import com.gis.warplan.domain.GisServer;
import com.gis.warplan.domain.PublishJob;
import com.gis.warplan.domain.PublishJobItem;
import com.gis.warplan.repository.PublishJobItemRepository;
import com.gis.warplan.repository.PublishJobRepository;
import com.gis.warplan.service.geoserver.GeoServerRestClient;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.NotFoundException;

@Service
public class PublishJobService {

    private final PublishJobRepository jobs;
    private final PublishJobItemRepository items;
    private final MapCatalogService catalog;
    private final GeoServerConnectionService connections;
    private final GeoServerRestClient client;
    private final WarplanProperties properties;
    private final AuditService audit;
    private final AccessService access;

    public PublishJobService(PublishJobRepository jobs, PublishJobItemRepository items, MapCatalogService catalog,
            GeoServerConnectionService connections, GeoServerRestClient client, WarplanProperties properties,
            AuditService audit, AccessService access) {
        this.jobs = jobs;
        this.items = items;
        this.catalog = catalog;
        this.connections = connections;
        this.client = client;
        this.properties = properties;
        this.audit = audit;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public PublishJob getJob(Long id) {
        return jobs.findById(id).orElseThrow(() -> new NotFoundException("Publish job not found."));
    }

    @Transactional(readOnly = true)
    public List<PublishJob> listJobs() {
        return jobs.findTop50ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public List<PublishJobItem> listItems(Long jobId) {
        getJob(jobId);
        return items.findByJobIdOrderBySortOrderAscIdAsc(jobId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dryRun(DryRunRequest request) {
        GisServer server = catalog.getServer(request.gisServerId());
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> itemResults = new ArrayList<>();
        boolean ok = true;
        try {
            connections.connectionFor(server);
            client.aboutVersion(connections.connectionFor(server));
        } catch (Exception ex) {
            ok = false;
            result.put("connectionError", ex.getMessage());
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("At least one item is required.");
        }
        if (request.items().size() > properties.getPublish().getMaxItemsPerJob()) {
            throw new BadRequestException("A job may include at most " + properties.getPublish().getMaxItemsPerJob()
                    + " items.");
        }
        for (DryRunItem item : request.items()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("clientKey", item.clientKey());
            row.put("targetLayerName", item.targetLayerName());
            row.put("sourceType", item.sourceType());
            List<String> problems = new ArrayList<>();
            if (item.targetLayerName() == null || item.targetLayerName().isBlank()) {
                problems.add("Target layer name is required.");
            }
            String type = detectOrRequireType(item.sourceType(), item.fileName(), item.tableName());
            row.put("resolvedType", type);
            if (PublishJobItem.POSTGIS_TABLE.equals(type) && (item.tableName() == null || item.tableName().isBlank())) {
                problems.add("PostGIS table name is required.");
            }
            if (!PublishJobItem.POSTGIS_TABLE.equals(type) && (item.fileName() == null || item.fileName().isBlank())) {
                problems.add("File name is required.");
            }
            String workspace = blank(request.workspace(), server.getWorkspaceDefault(), "ne");
            String qualified = workspace + ":" + sanitizeName(item.targetLayerName());
            try {
                if (client.layerExists(connections.connectionFor(server), qualified)) {
                    row.put("existsOnGeoServer", true);
                    row.put("conflictPolicy", blank(request.overwritePolicy(), PublishJob.POLICY_SKIP));
                } else {
                    row.put("existsOnGeoServer", false);
                }
            } catch (Exception ex) {
                row.put("existsCheckError", ex.getMessage());
            }
            row.put("ok", problems.isEmpty());
            row.put("problems", problems);
            if (!problems.isEmpty()) {
                ok = false;
            }
            itemResults.add(row);
        }
        result.put("ok", ok && !result.containsKey("connectionError"));
        result.put("items", itemResults);
        return result;
    }

    @Transactional
    public PublishJob createFileJob(Long gisServerId, String workspace, String overwritePolicy, String defaultCrs,
            List<ManifestItem> manifest, List<MultipartFile> files) throws IOException {
        GisServer server = catalog.getServer(gisServerId);
        if (manifest == null || manifest.isEmpty()) {
            throw new BadRequestException("Manifest is required.");
        }
        if (manifest.size() > properties.getPublish().getMaxItemsPerJob()) {
            throw new BadRequestException("Too many items in this job.");
        }
        Map<String, MultipartFile> fileByField = new LinkedHashMap<>();
        List<MultipartFile> orderedFiles = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    orderedFiles.add(file);
                    if (file.getOriginalFilename() != null) {
                        fileByField.put(file.getName(), file);
                        fileByField.put(file.getOriginalFilename(), file);
                    }
                }
            }
        }
        PublishJob job = newJob(server, workspace, overwritePolicy, defaultCrs, manifest.size());
        Path jobDir = stagingDir(job.getId());
        Files.createDirectories(jobDir);
        int order = 0;
        for (ManifestItem entry : manifest) {
            MultipartFile file = resolveFile(fileByField, entry);
            if ((file == null || file.isEmpty()) && order < orderedFiles.size()) {
                file = orderedFiles.get(order);
            }
            if (file == null || file.isEmpty()) {
                throw new BadRequestException("Missing file for item " + entry.targetLayerName());
            }
            if (file.getSize() > properties.getPublish().getMaxFileBytes()) {
                throw new BadRequestException("File exceeds the maximum size: " + file.getOriginalFilename());
            }
            String type = detectOrRequireType(entry.sourceType(), file.getOriginalFilename(), null);
            Path staged = jobDir.resolve(order + "-" + sanitizeFile(file.getOriginalFilename()));
            Files.copy(file.getInputStream(), staged, StandardCopyOption.REPLACE_EXISTING);
            PublishJobItem item = new PublishJobItem();
            item.setJobId(job.getId());
            item.setSortOrder(order++);
            item.setSourceType(type);
            item.setSourceFileName(file.getOriginalFilename());
            item.setStagedPath(staged.toAbsolutePath().toString());
            item.setTargetLayerName(sanitizeName(entry.targetLayerName()));
            item.setTitleLabel(blankToNull(entry.title()));
            item.setGroupCode(blankToNull(entry.groupCode()));
            item.setStyleName(blankToNull(entry.styleName()));
            item.setStatus(PublishJobItem.QUEUED);
            items.save(item);
        }
        jobs.save(job);
        audit.record("CREATE", "PublishJob", job.getId(), "Publish job created with " + job.getTotalItems() + " items",
                Map.of());
        return job;
    }

    @Transactional
    public PublishJob createPostgisJob(PostgisJobRequest request) {
        GisServer server = catalog.getServer(request.gisServerId());
        if (request.tables() == null || request.tables().isEmpty()) {
            throw new BadRequestException("Select at least one PostGIS table.");
        }
        if (request.tables().size() > properties.getPublish().getMaxItemsPerJob()) {
            throw new BadRequestException("Too many tables in this job.");
        }
        String storeName = blank(request.storeName(), "postgis");
        connections.connectionFor(server);
        String workspace = blank(request.workspace(), server.getWorkspaceDefault(), "ne");
        client.ensurePostgisDatastore(connections.connectionFor(server), workspace, storeName, request.host(),
                request.port() == null ? 5432 : request.port(), request.database(),
                blank(request.schema(), "public"), request.username(), request.password());
        PublishJob job = newJob(server, workspace, request.overwritePolicy(), request.defaultCrs(), request.tables().size());
        int order = 0;
        for (PostgisTableItem table : request.tables()) {
            PublishJobItem item = new PublishJobItem();
            item.setJobId(job.getId());
            item.setSortOrder(order++);
            item.setSourceType(PublishJobItem.POSTGIS_TABLE);
            item.setTableName(table.tableName());
            item.setPostgisStoreName(storeName);
            item.setTargetLayerName(sanitizeName(blank(table.targetLayerName(), table.tableName())));
            item.setTitleLabel(blankToNull(table.title()));
            item.setGroupCode(blankToNull(table.groupCode()));
            item.setStyleName(blankToNull(table.styleName()));
            item.setStatus(PublishJobItem.QUEUED);
            items.save(item);
        }
        jobs.save(job);
        audit.record("CREATE", "PublishJob", job.getId(), "PostGIS publish job created", Map.of());
        return job;
    }

    @Transactional
    public PublishJob cancel(Long jobId) {
        PublishJob job = getJob(jobId);
        if (isTerminal(job.getStatus())) {
            return job;
        }
        job.setStatus(PublishJob.CANCELLED);
        job.setMessage("Cancelled by user.");
        for (PublishJobItem item : items.findByJobIdOrderBySortOrderAscIdAsc(jobId)) {
            if (!isItemTerminal(item.getStatus())) {
                item.setStatus(PublishJobItem.CANCELLED);
                items.save(item);
            }
        }
        return jobs.save(job);
    }

    @Transactional
    public PublishJob retryFailed(Long jobId) {
        PublishJob job = getJob(jobId);
        List<PublishJobItem> failed = items.findByJobIdAndStatusOrderBySortOrderAscIdAsc(jobId, PublishJobItem.FAILED);
        if (failed.isEmpty()) {
            throw new BadRequestException("There are no failed items to retry.");
        }
        for (PublishJobItem item : failed) {
            item.setStatus(PublishJobItem.QUEUED);
            item.setErrorCode(null);
            item.setErrorMessage(null);
            items.save(item);
        }
        job.setStatus(PublishJob.QUEUED);
        job.setFailedItems(0);
        job.setMessage("Retrying failed items.");
        return jobs.save(job);
    }

    private PublishJob newJob(GisServer server, String workspace, String overwritePolicy, String defaultCrs, int total) {
        PublishJob job = new PublishJob();
        job.setGisServerId(server.getId());
        job.setWorkspace(blank(workspace, server.getWorkspaceDefault(), "ne"));
        job.setOverwritePolicy(normalizePolicy(overwritePolicy));
        job.setDefaultCrs(blank(defaultCrs, "EPSG:4326"));
        job.setStatus(PublishJob.QUEUED);
        job.setTotalItems(total);
        access.currentPrincipal().ifPresent(principal -> job.setCreatedByUserId(principal.userId()));
        return jobs.save(job);
    }

    private Path stagingDir(Long jobId) throws IOException {
        Path root = Path.of(properties.getPublish().getStagingRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root.resolve("job-" + jobId + "-" + UUID.randomUUID());
    }

    private static MultipartFile resolveFile(Map<String, MultipartFile> files, ManifestItem entry) {
        if (entry.fileField() != null && files.containsKey(entry.fileField())) {
            return files.get(entry.fileField());
        }
        if (entry.fileName() != null && files.containsKey(entry.fileName())) {
            return files.get(entry.fileName());
        }
        List<MultipartFile> matches = files.values().stream()
                .filter(file -> entry.fileName() != null
                        && entry.fileName().equalsIgnoreCase(file.getOriginalFilename()))
                .toList();
        if (matches.size() == 1) {
            return matches.get(0);
        }
        // Fall back to positional match via fileField index encoded as files[n] is not available;
        // use first unused match by original name when unique enough.
        return matches.stream().findFirst().orElse(null);
    }

    static String detectOrRequireType(String sourceType, String fileName, String tableName) {
        if (sourceType != null && !sourceType.isBlank()) {
            return sourceType.trim().toUpperCase(Locale.ROOT);
        }
        if (tableName != null && !tableName.isBlank()) {
            return PublishJobItem.POSTGIS_TABLE;
        }
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return PublishJobItem.SHAPEFILE_ZIP;
        }
        if (lower.endsWith(".geojson") || lower.endsWith(".json")) {
            return PublishJobItem.GEOJSON;
        }
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) {
            return PublishJobItem.GEOTIFF;
        }
        throw new BadRequestException("Unsupported or unknown source type for " + fileName);
    }

    static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Layer name is required.");
        }
        String cleaned = name.trim().replaceAll("[^A-Za-z0-9_\\-]+", "_");
        if (cleaned.isBlank()) {
            throw new BadRequestException("Layer name is invalid.");
        }
        return cleaned;
    }

    private static String sanitizeFile(String name) {
        String cleaned = name == null ? "upload.bin" : name.replaceAll("[\\\\/]+", "_");
        return cleaned.length() > 180 ? cleaned.substring(cleaned.length() - 180) : cleaned;
    }

    private static String normalizePolicy(String policy) {
        String value = policy == null ? PublishJob.POLICY_SKIP : policy.trim().toUpperCase(Locale.ROOT);
        if (!PublishJob.POLICY_SKIP.equals(value) && !PublishJob.POLICY_RENAME.equals(value)
                && !PublishJob.POLICY_OVERWRITE.equals(value)) {
            return PublishJob.POLICY_SKIP;
        }
        return value;
    }

    static boolean isTerminal(String status) {
        return PublishJob.COMPLETED.equals(status) || PublishJob.PARTIAL.equals(status)
                || PublishJob.FAILED.equals(status) || PublishJob.CANCELLED.equals(status);
    }

    static boolean isItemTerminal(String status) {
        return PublishJobItem.SUCCEEDED.equals(status) || PublishJobItem.FAILED.equals(status)
                || PublishJobItem.SKIPPED.equals(status) || PublishJobItem.CANCELLED.equals(status);
    }

    private static String blank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record DryRunRequest(Long gisServerId, String workspace, String overwritePolicy, List<DryRunItem> items) {
    }

    public record DryRunItem(String clientKey, String sourceType, String fileName, String tableName,
            String targetLayerName, String groupCode) {
    }

    public record ManifestItem(String clientKey, String fileField, String fileName, String sourceType,
            String targetLayerName, String groupCode, String styleName, String title) {
    }

    public record PostgisJobRequest(Long gisServerId, String workspace, String overwritePolicy, String defaultCrs,
            String storeName, String host, Integer port, String database, String schema, String username,
            String password, List<PostgisTableItem> tables) {
    }

    public record PostgisTableItem(String tableName, String targetLayerName, String groupCode, String styleName,
            String title) {
    }
}
