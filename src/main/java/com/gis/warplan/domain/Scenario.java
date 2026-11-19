package com.gis.warplan.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "scenario")
@Getter
@Setter
public class Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    /** Sanitized HTML rich text (general idea). */
    @Column(columnDefinition = "text")
    private String generalIdea;

    /** Sanitized HTML rich text (opening narrative). */
    @Column(columnDefinition = "text")
    private String openingNarrative;

    /** Sanitized HTML rich text (special idea). */
    @Column(columnDefinition = "text")
    private String specialIdea;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean active;

    @Column(length = 500)
    private String attachedFile;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Long createdBy;

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
