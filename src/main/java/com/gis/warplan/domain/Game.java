package com.gis.warplan.domain;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "game")
@Getter
@Setter
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    private Long scenarioId;

    /** Free-form plain text describing the moon phases of the game. */
    @Column(length = 4000)
    private String moonphases;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean active;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> configJson;

    private Instant startedAt;

    private Instant endedAt;

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
