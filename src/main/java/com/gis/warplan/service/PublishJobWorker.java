package com.gis.warplan.service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.config.WarplanProperties;
import com.gis.warplan.domain.GisServer;
import com.gis.warplan.domain.MapLayer;
import com.gis.warplan.domain.PublishJob;
import com.gis.warplan.domain.PublishJobItem;
import com.gis.warplan.repository.PublishJobItemRepository;
import com.gis.warplan.repository.PublishJobRepository;
import com.gis.warplan.service.geoserver.GeoServerException;
import com.gis.warplan.service.geoserver.GeoServerRestClient;
import com.gis.warplan.service.geoserver.GeoServerRestClient.Connection;

@Component
public class PublishJobWorker {

    private static final Logger log = LoggerFactory.getLogger(PublishJobWorker.class);

    private final PublishJobRepository jobs;
    private final PublishJobItemRepository items;
    private final MapCatalogService catalog;
    private final MapLayerGroupService groups;
    private final GeoServerConnectionService connections;
    private final GeoServerRestClient client;
    private final WarplanProperties properties;
    private final AtomicInteger inflight = new AtomicInteger();

    public PublishJobWorker(PublishJobRepository jobs, PublishJobItemRepository items, MapCatalogService catalog,
            MapLayerGroupService groups, GeoServerConnectionService connections, GeoServerRestClient client,
            WarplanProperties properties) {
        this.jobs = jobs;
        this.items = items;
        this.catalog = catalog;
        this.groups = groups;
        this.connections = connections;
        this.client = client;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 2000)
    public void tick() {
        int max = Math.max(1, properties.getPublish().getWorkerConcurrency());
        if (inflight.get() >= max) {
            return;
        }
        List<PublishJob> active = jobs.findByStatusInOrderByIdAsc(
                List.of(PublishJob.QUEUED, PublishJob.VALIDATING, PublishJob.RUNNING));
        for (PublishJob job : active) {
            List<PublishJobItem> queued = items.findByJobIdAndStatusOrderBySortOrderAscIdAsc(job.getId(),
                    PublishJobItem.QUEUED);
            if (queued.isEmpty()) {
                finalizeJob(job.getId());
                continue;
            }
            if (inflight.incrementAndGet() > max) {
                inflight.decrementAndGet();
                return;
            }
            try {
                processItem(job.getId(), queued.get(0).getId());
            } finally {
                inflight.decrementAndGet();
            }
            return;
        }
    }

    @Transactional
    public void processItem(Long jobId, Long itemId) {
        PublishJob job = jobs.findById(jobId).orElse(null);
        PublishJobItem item = items.findById(itemId).orElse(null);
        if (job == null || item == null) {
            return;
        }
        if (PublishJob.CANCELLED.equals(job.getStatus()) || PublishJobItem.CANCELLED.equals(item.getStatus())) {
            return;
        }
        job.setStatus(PublishJob.RUNNING);
        jobs.save(job);
        item.setAttempts(item.getAttempts() + 1);
        item.setStatus(PublishJobItem.VALIDATING);
        items.save(item);
        try {
            GisServer server = catalog.getServer(job.getGisServerId());
            Connection connection = connections.connectionFor(server);
            client.ensureWorkspace(connection, job.getWorkspace());
            String target = item.getTargetLayerName();
            String qualified = job.getWorkspace() + ":" + target;
            boolean exists = client.layerExists(connection, qualified);
            if (exists) {
                if (PublishJob.POLICY_SKIP.equals(job.getOverwritePolicy())) {
                    item.setStatus(PublishJobItem.SKIPPED);
                    item.setErrorCode("EXISTS");
                    item.setErrorMessage("Layer already exists on GeoServer; skipped.");
                    item.setGsLayerName(qualified);
                    items.save(item);
                    return;
                }
                if (PublishJob.POLICY_RENAME.equals(job.getOverwritePolicy())) {
                    target = uniqueName(connection, job.getWorkspace(), target);
                    item.setTargetLayerName(target);
                    qualified = job.getWorkspace() + ":" + target;
                }
                // OVERWRITE: GeoServer file upload with configure=first typically replaces store content
            }

            item.setStatus(PublishJobItem.UPLOADING);
            items.save(item);
            Path staged = item.getStagedPath() == null ? null : Path.of(item.getStagedPath());
            switch (item.getSourceType()) {
                case PublishJobItem.SHAPEFILE_ZIP -> {
                    if (staged == null || !Files.exists(staged)) {
                        throw new GeoServerException("Staged shapefile zip is missing.", 400);
                    }
                    client.uploadShapefileZip(connection, job.getWorkspace(), target, staged, false);
                }
                case PublishJobItem.GEOJSON -> {
                    if (staged == null || !Files.exists(staged)) {
                        throw new GeoServerException("Staged GeoJSON is missing.", 400);
                    }
                    client.uploadGeoJson(connection, job.getWorkspace(), target, staged);
                }
                case PublishJobItem.GEOTIFF -> {
                    if (staged == null || !Files.exists(staged)) {
                        throw new GeoServerException("Staged GeoTIFF is missing.", 400);
                    }
                    client.uploadGeoTiff(connection, job.getWorkspace(), target, staged);
                }
                case PublishJobItem.POSTGIS_TABLE -> {
                    item.setStatus(PublishJobItem.PUBLISHING);
                    items.save(item);
                    client.publishFeatureType(connection, job.getWorkspace(),
                            item.getPostgisStoreName() == null ? "postgis" : item.getPostgisStoreName(),
                            item.getTableName(), job.getDefaultCrs());
                    target = item.getTableName();
                    qualified = job.getWorkspace() + ":" + target;
                    item.setTargetLayerName(target);
                }
                default -> throw new GeoServerException("Unsupported source type: " + item.getSourceType(), 400);
            }

            item.setStatus(PublishJobItem.PUBLISHING);
            items.save(item);
            if (item.getStyleName() != null && !item.getStyleName().isBlank()) {
                try {
                    client.setLayerDefaultStyle(connection, job.getWorkspace(), qualified, item.getStyleName());
                } catch (GeoServerException ex) {
                    log.warn("Style bind failed for {}: {}", qualified, ex.getMessage());
                }
            }

            item.setStatus(PublishJobItem.SYNCING);
            items.save(item);
            MapLayer layer = syncCatalogLayer(job, item, qualified);
            item.setMapLayerId(layer.getId());
            item.setGsLayerName(qualified);
            item.setStatus(PublishJobItem.SUCCEEDED);
            item.setErrorCode(null);
            item.setErrorMessage(null);
            items.save(item);
            maybeFinalizeGroup(job, item);
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (Exception ignored) {
                    // best effort
                }
            }
        } catch (GeoServerException ex) {
            handleFailure(item, ex, ex.isRetryable());
        } catch (Exception ex) {
            handleFailure(item, ex, false);
        }
    }

    private void handleFailure(PublishJobItem item, Exception ex, boolean retryable) {
        log.warn("Publish item {} failed: {}", item.getId(), ex.getMessage());
        if (retryable && item.getAttempts() < 3) {
            item.setStatus(PublishJobItem.QUEUED);
            item.setErrorCode("RETRY");
            item.setErrorMessage(ex.getMessage());
        } else {
            item.setStatus(PublishJobItem.FAILED);
            item.setErrorCode(ex instanceof GeoServerException gs && gs.isUnauthorized() ? "AUTH" : "ERROR");
            item.setErrorMessage(ex.getMessage());
        }
        items.save(item);
    }

    private MapLayer syncCatalogLayer(PublishJob job, PublishJobItem item, String qualified) {
        String label = item.getTitleLabel() == null || item.getTitleLabel().isBlank() ? item.getTargetLayerName()
                : item.getTitleLabel();
        return catalog.findByServerAndWms(job.getGisServerId(), qualified).map(existing -> catalog.updateLayer(
                existing.getId(), job.getGisServerId(), existing.getCode(), label,
                item.getGroupCode() == null ? existing.getGroupName() : item.getGroupCode(), qualified,
                existing.getFormat(), existing.isTransparent(), existing.isBaseLayer(), existing.getSortOrder(),
                existing.isDefaultVisible(), existing.getDefaultOpacity(), existing.getMinZoom(), existing.getMaxZoom(),
                existing.getCqlFilter(), existing.getAttribution(), true)).orElseGet(() -> {
                    String code = item.getTargetLayerName().toLowerCase().replaceAll("[^a-z0-9_\\-]+", "-");
                    return catalog.createLayer(job.getGisServerId(), uniqueCode(code), label, item.getGroupCode(),
                            qualified, "image/png", true, false, 100 + item.getSortOrder(), false, BigDecimal.ONE, null,
                            null, null, null, true);
                });
    }

    private String uniqueCode(String base) {
        String code = base;
        int i = 2;
        while (catalog.listLayers().stream().anyMatch(layer -> layer.getCode().equals(code))) {
            code = base + "-" + i++;
        }
        return code;
    }

    private String uniqueName(Connection connection, String workspace, String base) {
        String candidate = base;
        int i = 2;
        while (client.layerExists(connection, workspace + ":" + candidate)) {
            candidate = base + "_" + i++;
        }
        return candidate;
    }

    @Transactional
    public void maybeFinalizeGroup(PublishJob job, PublishJobItem item) {
        if (item.getGroupCode() == null || item.getGroupCode().isBlank()) {
            return;
        }
        List<PublishJobItem> peers = items.findByJobIdOrderBySortOrderAscIdAsc(job.getId()).stream()
                .filter(peer -> item.getGroupCode().equals(peer.getGroupCode())).toList();
        boolean allTerminal = peers.stream().allMatch(peer -> PublishJobService.isItemTerminal(peer.getStatus()));
        if (!allTerminal) {
            return;
        }
        List<Long> layerIds = new ArrayList<>();
        for (PublishJobItem peer : peers) {
            if (PublishJobItem.SUCCEEDED.equals(peer.getStatus()) && peer.getMapLayerId() != null) {
                layerIds.add(peer.getMapLayerId());
            }
        }
        if (layerIds.isEmpty()) {
            return;
        }
        String gsName = job.getWorkspace() + ":" + item.getGroupCode();
        groups.upsertFromPublish(job.getGisServerId(), item.getGroupCode(), item.getGroupCode(), gsName, layerIds);
    }

    @Transactional
    public void finalizeJob(Long jobId) {
        PublishJob job = jobs.findById(jobId).orElse(null);
        if (job == null || PublishJobService.isTerminal(job.getStatus())) {
            return;
        }
        List<PublishJobItem> all = items.findByJobIdOrderBySortOrderAscIdAsc(jobId);
        if (all.stream().anyMatch(item -> !PublishJobService.isItemTerminal(item.getStatus()))) {
            return;
        }
        int ok = (int) all.stream().filter(item -> PublishJobItem.SUCCEEDED.equals(item.getStatus())).count();
        int failed = (int) all.stream().filter(item -> PublishJobItem.FAILED.equals(item.getStatus())).count();
        int skipped = (int) all.stream().filter(item -> PublishJobItem.SKIPPED.equals(item.getStatus())).count();
        job.setSucceededItems(ok);
        job.setFailedItems(failed);
        job.setSkippedItems(skipped);
        if (PublishJob.CANCELLED.equals(job.getStatus())
                || all.stream().anyMatch(item -> PublishJobItem.CANCELLED.equals(item.getStatus())) && ok == 0
                        && failed == 0) {
            job.setStatus(PublishJob.CANCELLED);
        } else if (failed > 0 && ok > 0) {
            job.setStatus(PublishJob.PARTIAL);
            job.setMessage("Completed with failures.");
        } else if (failed > 0) {
            job.setStatus(PublishJob.FAILED);
            job.setMessage("All items failed.");
        } else {
            job.setStatus(PublishJob.COMPLETED);
            job.setMessage("Completed.");
        }
        jobs.save(job);
    }
}
