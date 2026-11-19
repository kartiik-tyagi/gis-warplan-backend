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

    /** Base icon folder every icon call is resolved against. Default: the configured {@code warplan.icons.root}. */
    @Column(nullable = false, length = 500)
    private String path;

    /**
     * Per-user icon labels: outer key is the user id, inner map is that user's label per icon relative path
     * (e.g. {@code {"7": {"circle.svg": "HQ"}}}). Stored as a lenient {@code Map<String, Object>} so an older
     * flat (shared) labels value still deserializes; such legacy entries are ignored.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> labels = new LinkedHashMap<>();

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
