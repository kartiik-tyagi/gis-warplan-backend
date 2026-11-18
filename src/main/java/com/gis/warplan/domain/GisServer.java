package com.gis.warplan.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gis_server", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter
@Setter
public class GisServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 500)
    private String wmsUrl;

    @Column(length = 500)
    private String wfsUrl;

    @Column(length = 500)
    private String restUrl;

    @Column(length = 120)
    private String restUsername;

    @Column(columnDefinition = "text")
    private String restPasswordEnc;

    @Column(length = 120)
    private String workspaceDefault;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean defaultServer;

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
