package com.gis.warplan.domain;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Role master. {@code code} is an internal system key (seeds / auth checks) and is not part of the public role API.
 */
@Entity
@Table(name = "role", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter
@Setter
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Internal system key — not exposed on role CRUD responses. */
    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Long> functionalityIds;

    /** True for seeded/system roles that ship with the product. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean standard;

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
