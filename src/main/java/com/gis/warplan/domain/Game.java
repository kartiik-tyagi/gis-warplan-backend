package com.gis.warplan.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    @Column(nullable = false)
    private Long scenarioId;

    @Column(nullable = false, length = 160)
    private String name;

    private LocalDateTime dateTime;

    private LocalTime sunrise;

    private LocalTime sunset;

    /** Free-form plain text describing the moon phases of the game. */
    @Column(length = 4000)
    private String moonphases;

    @Column(length = 4000)
    private String description;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean active;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

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
