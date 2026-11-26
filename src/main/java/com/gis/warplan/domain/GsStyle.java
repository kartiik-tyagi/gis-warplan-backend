package com.gis.warplan.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gs_style", uniqueConstraints = @UniqueConstraint(columnNames = { "gisServerId", "name" }))
@Getter
@Setter
public class GsStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long gisServerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String workspace;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
