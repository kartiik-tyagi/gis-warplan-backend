package com.gis.warplan.domain;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_log", indexes = @Index(columnList = "occurredAt"))
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant occurredAt;

    private Long actorUserId;

    private Long sessionId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 80)
    private String entityType;

    private Long entityId;

    @Column(length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> detailJson;

    @Column(length = 80)
    private String ipAddress;

    @Column(length = 400)
    private String userAgent;

    @Column(length = 80)
    private String requestId;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
