package com.gis.warplan.service;

import java.time.Instant;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.AuditLog;
import com.gis.warplan.repository.AuditLogRepository;
import com.gis.warplan.security.UserPrincipal;
import com.gis.warplan.web.RequestIdFilter;

@Service
public class AuditService {

    private static final Logger log = LogManager.getLogger(AuditService.class);

    private final AuditLogRepository audits;
    private final AccessService access;

    public AuditService(AuditLogRepository audits, AccessService access) {
        this.audits = audits;
        this.access = access;
    }

    @Transactional
    public AuditLog record(String action, String entityType, Long entityId, String summary,
            Map<String, Object> detail) {
        AuditLog entry = new AuditLog();
        entry.setOccurredAt(Instant.now());
        access.currentPrincipal().ifPresent(principal -> apply(entry, principal));
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setSummary(summary == null || summary.length() <= 500 ? summary : summary.substring(0, 500));
        entry.setDetailJson(detail);
        entry.setRequestId(ThreadContext.get(RequestIdFilter.KEY));
        AuditLog saved = audits.save(entry);
        log.info("audit.recorded action={} entityType={} entityId={} actorUserId={}", action, entityType, entityId,
                saved.getActorUserId());
        return saved;
    }

    private static void apply(AuditLog entry, UserPrincipal principal) {
        entry.setActorUserId(principal.userId());
    }
}
