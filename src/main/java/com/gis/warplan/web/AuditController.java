package com.gis.warplan.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.AuditLog;
import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.UserSession;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.AuditLogRepository;
import com.gis.warplan.repository.UserSessionRepository;
import com.gis.warplan.service.AccessService;

import jakarta.persistence.criteria.Predicate;

/** Read-only audit trail; needs VIEW_AUDIT. Newest entries first. */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository logs;
    private final UserSessionRepository sessions;
    private final AppUserRepository users;
    private final AccessService access;

    public AuditController(AuditLogRepository logs, UserSessionRepository sessions, AppUserRepository users,
            AccessService access) {
        this.logs = logs;
        this.sessions = sessions;
        this.users = users;
        this.access = access;
    }

    @GetMapping("/logs")
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> logs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        access.requireFunctionality(FunctionalityCodes.VIEW_AUDIT);
        Specification<AuditLog> filter = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(builder.equal(root.get("actorUserId"), userId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(builder.equal(root.get("action"), action.trim()));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(builder.equal(root.get("entityType"), entityType.trim()));
            }
            if (entityId != null) {
                predicates.add(builder.equal(root.get("entityId"), entityId));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        Page<AuditLog> result = logs.findAll(filter, pageable(page, size, "occurredAt"));
        Map<Long, String> names = new HashMap<>();
        for (AuditLog entry : result) {
            if (entry.getActorUserId() != null) {
                names.computeIfAbsent(entry.getActorUserId(),
                        id -> users.findById(id).map(AppUser::getName).orElse(null));
            }
        }
        return PageResponse.of(result, entry -> AuditLogResponse.of(entry, names.get(entry.getActorUserId())));
    }

    /** Sign-in sessions. The token id is never returned. */
    @GetMapping("/sessions")
    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> sessions(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        access.requireFunctionality(FunctionalityCodes.VIEW_AUDIT);
        var pageable = pageable(page, size, "loginAt");
        Page<UserSession> result = userId == null ? sessions.findAll(pageable) : sessions.findByUserId(userId, pageable);
        Map<Long, String> names = new HashMap<>();
        for (UserSession session : result) {
            names.computeIfAbsent(session.getUserId(),
                    id -> users.findById(id).map(AppUser::getName).orElse(null));
        }
        Instant now = Instant.now();
        return PageResponse.of(result, session -> new SessionResponse(session.getId(), session.getUserId(),
                names.get(session.getUserId()), session.getLoginAt(), session.getLogoutAt(), session.isSuccess(),
                session.getFailureReason(), session.getIpAddress(), session.getUserAgent(), session.getLocationText(),
                session.getExpiresAt(), session.getRevokedAt(),
                session.getRevokedAt() == null && session.getExpiresAt().isAfter(now)));
    }

    private static PageRequest pageable(int page, int size, String sortField) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortField).and(Sort.by(Sort.Direction.DESC, "id")));
    }

    public record PageResponse<T>(List<T> items, int page, int size, long total) {

        static <S, T> PageResponse<T> of(Page<S> page, java.util.function.Function<S, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements());
        }
    }

    public record AuditLogResponse(Long id, Instant occurredAt, Long actorUserId, String actorUserName, Long sessionId,
            String action, String entityType, Long entityId, String summary, Map<String, Object> detailJson,
            String ipAddress, String userAgent, String requestId) {

        static AuditLogResponse of(AuditLog log, String actorUserName) {
            return new AuditLogResponse(log.getId(), log.getOccurredAt(), log.getActorUserId(), actorUserName,
                    log.getSessionId(), log.getAction(), log.getEntityType(), log.getEntityId(), log.getSummary(),
                    log.getDetailJson(), log.getIpAddress(), log.getUserAgent(), log.getRequestId());
        }
    }

    public record SessionResponse(Long id, Long userId, String userName, Instant loginAt, Instant logoutAt,
            boolean success, String failureReason, String ipAddress, String userAgent, String locationText,
            Instant expiresAt, Instant revokedAt, boolean active) {
    }
}
