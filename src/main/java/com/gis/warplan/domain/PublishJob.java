package com.gis.warplan.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "publish_job")
@Getter
@Setter
public class PublishJob {

    public static final String QUEUED = "QUEUED";
    public static final String VALIDATING = "VALIDATING";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";

    public static final String POLICY_SKIP = "SKIP";
    public static final String POLICY_RENAME = "RENAME";
    public static final String POLICY_OVERWRITE = "OVERWRITE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long gisServerId;

    @Column(nullable = false, length = 120)
    private String workspace;

    @Column(nullable = false, length = 32)
    private String status = QUEUED;

    @Column(nullable = false, length = 32)
    private String overwritePolicy = POLICY_SKIP;

    @Column(length = 32)
    private String defaultCrs = "EPSG:4326";

    @Column(nullable = false)
    private int totalItems;

    @Column(nullable = false)
    private int succeededItems;

    @Column(nullable = false)
    private int failedItems;

    @Column(nullable = false)
    private int skippedItems;

    @Column(length = 1000)
    private String message;

    @Column
    private Long createdByUserId;

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
