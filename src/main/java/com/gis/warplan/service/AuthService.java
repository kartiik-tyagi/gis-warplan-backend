package com.gis.warplan.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.config.WarplanProperties;
import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.Theme;
import com.gis.warplan.domain.UserSession;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.UserSessionRepository;
import com.gis.warplan.security.JwtService;
import com.gis.warplan.security.JwtService.IssuedToken;
import com.gis.warplan.security.UserPrincipal;
import com.gis.warplan.service.ActivationService.Activation;
import com.gis.warplan.web.UnauthorizedException;
import com.gis.warplan.web.UnavailableException;

@Service
public class AuthService {

    private static final String INVALID = "Invalid name or password.";
    private static final Logger log = LogManager.getLogger(AuthService.class);

    private final AppUserRepository users;
    private final UserSessionRepository sessions;
    private final ThemeAllotmentService allotment;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccessService access;
    private final AuditService audit;
    private final ActivationService activation;
    private final UserService userService;
    private final WarplanProperties properties;

    public AuthService(AppUserRepository users, UserSessionRepository sessions, ThemeAllotmentService allotment,
            PasswordEncoder passwordEncoder, JwtService jwtService, AccessService access, AuditService audit,
            ActivationService activation, UserService userService, WarplanProperties properties) {
        this.users = users;
        this.sessions = sessions;
        this.allotment = allotment;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.access = access;
        this.audit = audit;
        this.activation = activation;
        this.userService = userService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public LoginContext loginContext() {
        Theme theme = allotment.loginTheme();
        if (theme == null) {
            log.error("login_context.unavailable reason=apex_theme_missing");
            throw new UnavailableException("The login theme is not configured.");
        }
        List<LoginUser> activeUsers = users.findByActiveTrueOrderByNameAsc().stream()
                .map(user -> new LoginUser(user.getId(), user.getName()))
                .toList();
        String name = properties.getApplicationName();
        log.info("login_context.served users={} themeId={}", activeUsers.size(), theme.getId());
        return new LoginContext(name, name, activeUsers, theme);
    }

    @Transactional
    public LoginResult login(String name, String password) {
        AppUser user = users.findByName(name).orElse(null);
        if (user == null || !user.isActive() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("login.rejected namePresent={}", name != null && !name.isBlank());
            throw new UnauthorizedException(INVALID);
        }
        userService.ensureMarkedSide(user);
        List<String> roleCodes = access.resolveRoleCodes(user);
        List<String> functionalityCodes = access.resolveFunctionalityCodes(user);
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getName(), roleCodes, functionalityCodes,
                user.getAdministeredSideId());
        IssuedToken issued = jwtService.create(principal);

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setSessionTokenId(issued.tokenId());
        session.setLoginAt(issued.issuedAt());
        session.setSuccess(true);
        session.setExpiresAt(issued.expiresAt());
        sessions.save(session);

        user.setLastLoginAt(Instant.now());
        users.save(user);
        log.info("login.ok userId={}", user.getId());
        audit.record("LOGIN", "AppUser", user.getId(), "User " + user.getName() + " signed in", Map.of());
        return new LoginResult(issued.token(), issued.expiresAt(), me(user));
    }

    @Transactional
    public void logout(UserPrincipal principal) {
        if (principal.tokenId() == null) {
            return;
        }
        sessions.findBySessionTokenId(principal.tokenId()).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                Instant now = Instant.now();
                session.setRevokedAt(now);
                session.setLogoutAt(now);
                sessions.save(session);
            }
        });
        log.info("logout.ok userId={}", principal.userId());
        audit.record("LOGOUT", "AppUser", principal.userId(), "User " + principal.name() + " signed out", Map.of());
    }

    @Transactional(readOnly = true)
    public MeResponse me(UserPrincipal principal) {
        AppUser user = users.findById(principal.userId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UnauthorizedException("Authentication is required."));
        return me(user);
    }

    private MeResponse me(AppUser user) {
        Activation state = activation.evaluate(user);
        return new MeResponse(
                user.getId(),
                user.getName(),
                access.resolveRoleCodes(user),
                access.resolveFunctionalityCodes(user),
                user.getAdministeredSideId(),
                access.resolveThemeId(user),
                user.getStandardOrbatId(),
                user.getStandardNodeId(),
                user.getPlayingOrbatId(),
                user.getPlayingNodeId(),
                state.canWork(),
                state.reason());
    }

    public record LoginResult(String token, Instant expiresAt, MeResponse me) {
    }

    public record LoginUser(Long id, String name) {
    }

    public record LoginContext(String applicationName, String exerciseTitle, List<LoginUser> activeUsers, Theme loginTheme) {
    }

    public record MeResponse(
            Long userId,
            String name,
            List<String> roleCodes,
            List<String> functionalityCodes,
            Long administeredSideId,
            Long themeId,
            Long standardOrbatId,
            String standardNodeId,
            Long playingOrbatId,
            String playingNodeId,
            boolean canWork,
            String workBlockedReason) {
    }
}
