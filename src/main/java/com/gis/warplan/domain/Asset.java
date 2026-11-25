package com.gis.warplan.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "asset", uniqueConstraints = @UniqueConstraint(columnNames = "storage_key"))
@Getter
@Setter
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetKind kind;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ApplicationSlot slot;

    /** Path relative to the configured asset folder. */
    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(nullable = false, length = 200)
    private String originalName;

    /** Shared name shown to every user. Null falls back to the original file name. */
    @Column(length = 120)
    private String label;

    @Column(nullable = false, length = 120)
    private String mediaType;

    @Column(nullable = false)
    private long sizeBytes;

    private Long createdBy;

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

    public String displayName() {
        if (label != null && !label.isBlank()) {
            return label;
        }
        return originalName == null ? "" : originalName;
    }
}
