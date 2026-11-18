package com.gis.warplan.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "map_layer", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter
@Setter
public class MapLayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long gisServerId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "group_name", length = 80)
    private String groupName;

    @Column(nullable = false, length = 255)
    private String wmsLayers;

    @Column(nullable = false, length = 64)
    private String format = "image/png";

    @Column(nullable = false)
    private boolean transparent;

    @Column(nullable = false)
    private boolean baseLayer;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean defaultVisible;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal defaultOpacity = new BigDecimal("1.00");

    private Integer minZoom;

    private Integer maxZoom;

    @Column(length = 500)
    private String cqlFilter;

    @Column(length = 255)
    private String attribution;

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
