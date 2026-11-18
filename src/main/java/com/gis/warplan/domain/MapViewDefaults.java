package com.gis.warplan.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "map_view_defaults")
@Getter
@Setter
public class MapViewDefaults {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Always GeoServer WMS; retained so existing databases keep a non-null column. */
    @Column(nullable = false, length = 16)
    private String basemapSource = "wms";

    @Column(nullable = false)
    private double centerLon;

    @Column(nullable = false)
    private double centerLat;

    @Column(nullable = false)
    private int zoom;

    @Column(nullable = false)
    private int minZoom;

    @Column(nullable = false)
    private int maxZoom;

    private Long defaultGisServerId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        basemapSource = "wms";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        basemapSource = "wms";
    }
}
