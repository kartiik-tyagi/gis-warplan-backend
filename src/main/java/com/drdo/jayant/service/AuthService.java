package com.drdo.jayant.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drdo.jayant.domain.AppUser;
import com.drdo.jayant.domain.UserSession;
import com.drdo.jayant.repository.AppUserRepository;
import com.drdo.jayant.repository.UserSessionRepository;
import com.drdo.jayant.security.JwtService;
import com.drdo.jayant.security.JwtService.IssuedToken;
import com.drdo.jayant.security.UserPrincipal;
import com.drdo.jayant.web.UnauthorizedException;

@Service
public class AuthService {

    private static final String INVALID = "Invalid name or password.";

    private final AppUserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccessService access;
    private final AuditService audit;

    public AuthService(AppUserRepository users, UserSessionRepository sessions, PasswordEncoder passwordEncoder,
            JwtService jwtService, AccessService access, AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.access = access;
        this.audit = audit;
    }

    @Transactional
    public LoginResult login(String name, String password) {
        AppUser user = users.findByName(name).orElse(null);
        if (user == null || !user.isActive() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID);
        }
        List<String> roleCodes = access.resolveRoleCodes(user);
        List<String> featureCodes = access.resolveFeatureCodes(user);
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getName(), roleCodes, featureCodes,
                user.getAdministeredTeamId());
        IssuedToken issued = jwtService.create(principal);

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setTokenId(issued.tokenId());
        session.setIssuedAt(issued.issuedAt());
        session.setExpiresAt(issued.expiresAt());
        sessions.save(session);

        user.setLastLoginAt(Instant.now());
        users.save(user);
        audit.record("LOGIN", "AppUser", user.getId(), "User " + user.getName() + " signed in", Map.of());
        return new LoginResult(issued.token(), issued.expiresAt(), me(user));
    }

    @Transactional
    public void logout(UserPrincipal principal) {
        if (principal.tokenId() == null) {
            return;
        }
        sessions.findByTokenId(principal.tokenId()).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                sessions.save(session);
            }
        });
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
        return new MeResponse(
                user.getId(),
                user.getName(),
                access.resolveRoleCodes(user),
                access.resolveFeatureCodes(user),
                user.getAdministeredTeamId(),
                access.resolveThemeId(user),
                user.getStandardOrbatId(),
                user.getStandardNodeId(),
                user.getPlayingOrbatId(),
                user.getPlayingNodeId());
    }

    public record LoginResult(String token, Instant expiresAt, MeResponse me) {
    }

    public record MeResponse(
            Long userId,
            String name,
            List<String> roleCodes,
            List<String> featureCodes,
            Long administeredTeamId,
            Long themeId,
            Long standardOrbatId,
            String standardNodeId,
            Long playingOrbatId,
            String playingNodeId) {
    }
}
