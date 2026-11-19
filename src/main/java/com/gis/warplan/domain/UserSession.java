package com.gis.warplan.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_session", uniqueConstraints = @UniqueConstraint(columnNames = "sessionTokenId"))
@Getter
@Setter
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant loginAt;

    private Instant logoutAt;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean success = true;

    @Column(length = 500)
    private String failureReason;

    @Column(length = 80)
    private String ipAddress;

    @Column(length = 400)
    private String userAgent;

    @Column(length = 200)
    private String locationText;

    /** JWT token id used for revoke lookups. */
    @Column(nullable = false, length = 60)
    private String sessionTokenId;

    @Column(nullable = false)
    private Instant createdAt;

    /** JWT expiry — required for revoke/expiry checks. */
    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (loginAt == null) {
            loginAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }
}
