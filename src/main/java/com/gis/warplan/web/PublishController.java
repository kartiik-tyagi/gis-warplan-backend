package com.gis.warplan.web;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.PublishJob;
import com.gis.warplan.domain.PublishJobItem;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.PublishJobService;
import com.gis.warplan.service.PublishJobService.DryRunRequest;
import com.gis.warplan.service.PublishJobService.ManifestItem;
import com.gis.warplan.service.PublishJobService.PostgisJobRequest;

@RestController
@RequestMapping("/api/v1/map/publish")
public class PublishController {

    private final PublishJobService publish;
    private final AccessService access;
    private final ObjectMapper mapper;

    public PublishController(PublishJobService publish, AccessService access, ObjectMapper mapper) {
        this.publish = publish;
        this.access = access;
        this.mapper = mapper;
    }

    @PostMapping("/dry-run")
    public Map<String, Object> dryRun(@RequestBody DryRunRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return publish.dryRun(request);
    }

    @PostMapping(path = "/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobResponse> createJob(@RequestPart("manifest") String manifestJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "gisServerId", required = false) String gisServerId,
            @RequestPart(value = "workspace", required = false) String workspace,
            @RequestPart(value = "overwritePolicy", required = false) String overwritePolicy,
            @RequestPart(value = "defaultCrs", required = false) String defaultCrs) throws IOException {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        ManifestEnvelope envelope = mapper.readValue(manifestJson, ManifestEnvelope.class);
        Long serverId = envelope.gisServerId() != null ? envelope.gisServerId()
                : (gisServerId == null ? null : Long.valueOf(gisServerId));
        List<ManifestItem> items = envelope.items() != null ? envelope.items()
                : mapper.readValue(manifestJson, new TypeReference<List<ManifestItem>>() {
                });
        PublishJob job = publish.createFileJob(serverId,
                envelope.workspace() != null ? envelope.workspace() : workspace,
                envelope.overwritePolicy() != null ? envelope.overwritePolicy() : overwritePolicy,
                envelope.defaultCrs() != null ? envelope.defaultCrs() : defaultCrs, items, files);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobResponse.of(job, publish.listItems(job.getId())));
    }

    @PostMapping("/jobs/postgis")
    public ResponseEntity<JobResponse> createPostgisJob(@RequestBody PostgisJobRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        PublishJob job = publish.createPostgisJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobResponse.of(job, publish.listItems(job.getId())));
    }

    @GetMapping("/jobs")
    public List<JobResponse> listJobs() {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return publish.listJobs().stream().map(job -> JobResponse.of(job, publish.listItems(job.getId()))).toList();
    }

    @GetMapping("/jobs/{id}")
    public JobResponse getJob(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return JobResponse.of(publish.getJob(id), publish.listItems(id));
    }

    @PostMapping("/jobs/{id}/retry")
    public JobResponse retry(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        PublishJob job = publish.retryFailed(id);
        return JobResponse.of(job, publish.listItems(id));
    }

    @PostMapping("/jobs/{id}/cancel")
    public JobResponse cancel(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        PublishJob job = publish.cancel(id);
        return JobResponse.of(job, publish.listItems(id));
    }

    public record ManifestEnvelope(Long gisServerId, String workspace, String overwritePolicy, String defaultCrs,
            List<ManifestItem> items) {
    }

    public record JobItemResponse(Long id, int sortOrder, String sourceType, String sourceFileName, String tableName,
            String targetLayerName, String titleLabel, String groupCode, String styleName, String status, int attempts,
            String errorCode, String errorMessage, String gsLayerName, Long mapLayerId) {
        static JobItemResponse of(PublishJobItem item) {
            return new JobItemResponse(item.getId(), item.getSortOrder(), item.getSourceType(), item.getSourceFileName(),
                    item.getTableName(), item.getTargetLayerName(), item.getTitleLabel(), item.getGroupCode(),
                    item.getStyleName(), item.getStatus(), item.getAttempts(), item.getErrorCode(),
                    item.getErrorMessage(), item.getGsLayerName(), item.getMapLayerId());
        }
    }

    public record JobResponse(Long id, Long gisServerId, String workspace, String status, String overwritePolicy,
            String defaultCrs, int totalItems, int succeededItems, int failedItems, int skippedItems, String message,
            Instant createdAt, Instant updatedAt, List<JobItemResponse> items) {
        static JobResponse of(PublishJob job, List<PublishJobItem> items) {
            return new JobResponse(job.getId(), job.getGisServerId(), job.getWorkspace(), job.getStatus(),
                    job.getOverwritePolicy(), job.getDefaultCrs(), job.getTotalItems(), job.getSucceededItems(),
                    job.getFailedItems(), job.getSkippedItems(), job.getMessage(), job.getCreatedAt(),
                    job.getUpdatedAt(), items.stream().map(JobItemResponse::of).toList());
        }
    }
}
