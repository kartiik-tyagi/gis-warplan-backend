package com.gis.warplan.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "map_layer_group", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter
@Setter
public class MapLayerGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long gisServerId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 255)
    private String gsLayerGroupName;

    @Column(nullable = false)
    private int sortOrder = 100;

    @Column(nullable = false)
    private boolean defaultVisible = false;

    @Column(nullable = false)
    private boolean active = true;

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
