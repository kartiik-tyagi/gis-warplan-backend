package com.gis.warplan.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Singleton configuration row for the icon asset store: the base folder the icon picker reads from,
 * plus a label for each icon file. There is only ever one row; {@code AssetManagementService} creates
 * it on first use with the configured default path ({@code warplan.icons.root}).
 */
@Entity
@Table(name = "asset_management")
@Getter
@Setter
public class AssetManagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Base asset folder. Default: {@code asset-repository}. May point at another disk on this server. */
    @Column(nullable = false, length = 500)
    private String path;

    /**
     * Public labels kept for the one-time flatten of the old per-user map. The live label is {@code asset.label}.
     * A nested value means the old shape (user id to path to label) and is flattened on startup.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> labels = new LinkedHashMap<>();

    /** Null uses the built-in default (512 KB). */
    private Long iconMaxBytes;

    /** Null uses the built-in default (10 MB). */
    private Long imageMaxBytes;

    /** Null uses the built-in default (50 MB). */
    private Long documentMaxBytes;

    /** When the row was last changed (exposed to clients as an epoch). */
    @Column(nullable = false)
    private Instant lastUpdated;

    /** Id of the app_user who last changed the row ({@code null} for the auto-created row). */
    private Long updatedBy;

    @PrePersist
    @PreUpdate
    void touch() {
        if (lastUpdated == null) {
            lastUpdated = Instant.now();
        }
    }
}
