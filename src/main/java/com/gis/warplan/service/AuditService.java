package com.gis.warplan.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.AuditLog;
import com.gis.warplan.repository.AuditLogRepository;
import com.gis.warplan.security.UserPrincipal;

@Service
public class AuditService {

    private final AuditLogRepository audits;
    private final AccessService access;

    public AuditService(AuditLogRepository audits, AccessService access) {
        this.audits = audits;
        this.access = access;
    }

    @Transactional
    public AuditLog record(String action, String entityType, Long entityId, String summary,
            Map<String, Object> detail) {
        AuditLog log = new AuditLog();
        access.currentPrincipal().ifPresent(principal -> apply(log, principal));
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setSummary(summary == null || summary.length() <= 500 ? summary : summary.substring(0, 500));
        log.setDetail(detail);
        return audits.save(log);
    }

    private static void apply(AuditLog log, UserPrincipal principal) {
        log.setUserId(principal.userId());
        log.setUserName(principal.name());
    }
}
