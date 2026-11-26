package com.gis.warplan.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "publish_job_item")
@Getter
@Setter
public class PublishJobItem {

    public static final String QUEUED = "QUEUED";
    public static final String VALIDATING = "VALIDATING";
    public static final String UPLOADING = "UPLOADING";
    public static final String PUBLISHING = "PUBLISHING";
    public static final String SYNCING = "SYNCING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED = "SKIPPED";
    public static final String CANCELLED = "CANCELLED";

    public static final String SHAPEFILE_ZIP = "SHAPEFILE_ZIP";
    public static final String GEOJSON = "GEOJSON";
    public static final String GEOTIFF = "GEOTIFF";
    public static final String POSTGIS_TABLE = "POSTGIS_TABLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 32)
    private String sourceType;

    @Column(length = 500)
    private String sourceFileName;

    @Column(length = 1000)
    private String stagedPath;

    @Column(length = 120)
    private String tableName;

    @Column(length = 120)
    private String postgisStoreName;

    @Column(nullable = false, length = 120)
    private String targetLayerName;

    @Column(length = 120)
    private String titleLabel;

    @Column(length = 64)
    private String groupCode;

    @Column(length = 120)
    private String styleName;

    @Column(nullable = false, length = 32)
    private String status = QUEUED;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 64)
    private String errorCode;

    @Column(length = 2000)
    private String errorMessage;

    @Column(length = 255)
    private String gsLayerName;

    @Column
    private Long mapLayerId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
