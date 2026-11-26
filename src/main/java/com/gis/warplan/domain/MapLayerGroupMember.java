package com.gis.warplan.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "map_layer_group_member", uniqueConstraints = @UniqueConstraint(columnNames = { "groupId", "mapLayerId" }))
@Getter
@Setter
public class MapLayerGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private Long mapLayerId;

    @Column(nullable = false)
    private int sortOrder = 100;

    @Column(length = 120)
    private String styleName;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
