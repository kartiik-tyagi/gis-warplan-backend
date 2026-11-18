package com.drdo.jayant.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "side")
@Getter
@Setter
public class Side {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** country or alliance. */
    @Column(nullable = false, length = 20)
    private String kind;

    @Column(length = 500)
    private String description;

    @Column(length = 20)
    private String colour;

    @Column(length = 300)
    private String icon;

    private Long themeId;

    /** Owning game; required for alliance sides, null for country sides. */
    private Long gameId;

    /** Denormalized from the game; always set from {@code game.getScenarioId()}, null for country sides. */
    private Long scenarioId;

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
