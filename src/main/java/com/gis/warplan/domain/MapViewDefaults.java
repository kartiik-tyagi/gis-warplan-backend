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

    /** Geographic CRS of the pointer. The map can use EPSG:4326 or EPSG:3857. */
    @Column(length = 32)
    private String dataCrs = "EPSG:4326";

    /** CRS the map is drawn in. */
    @Column(length = 32)
    private String viewCrs = "EPSG:3857";

    /** Catalog layer queried for land height and ocean depth. Empty means the footer shows no value. */
    private Long elevationLayerId;

    /** Feature property that holds the number. Empty means the first numeric property. */
    @Column(length = 80)
    private String elevationAttribute;

    /** True when a positive value is land height and a negative value is ocean depth. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean elevationPositiveUp = true;

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
