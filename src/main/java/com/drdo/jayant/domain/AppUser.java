package com.drdo.jayant.domain;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Long> roleIds;

    private Long administeredSideId;

    @Column(nullable = false)
    private boolean active = true;

    private Long themeId;

    private Long standardOrbatId;

    @Column(length = 120)
    private String standardNodeId;

    private Long playingOrbatId;

    @Column(length = 120)
    private String playingNodeId;

    private Instant lastLoginAt;

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
