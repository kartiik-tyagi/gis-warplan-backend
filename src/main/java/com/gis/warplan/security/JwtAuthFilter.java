package com.gis.warplan.security;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gis.warplan.repository.UserSessionRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads a Bearer token, checks it against the stored session and sets the security context.
 * Created by {@link SecurityConfig}; it is not a bean so the servlet container does not register it twice.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserSessionRepository sessions;

    public JwtAuthFilter(JwtService jwtService, UserSessionRepository sessions) {
        this.jwtService = jwtService;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIX)) {
            Optional<UserPrincipal> principal = jwtService.parse(header.substring(PREFIX.length()).trim());
            if (principal.isPresent() && sessionActive(principal.get())) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal.get(), null, authorities(principal.get()));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private boolean sessionActive(UserPrincipal principal) {
        if (principal.tokenId() == null) {
            return false;
        }
        return sessions.findBySessionTokenId(principal.tokenId())
                .filter(session -> session.getRevokedAt() == null)
                .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }

    private static List<GrantedAuthority> authorities(UserPrincipal principal) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        principal.roleCodes().forEach(code -> authorities.add(new SimpleGrantedAuthority("ROLE_" + code)));
        principal.functionalityCodes().forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        return authorities;
    }
}
