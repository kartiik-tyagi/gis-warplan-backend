package com.drdo.jayant.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.drdo.jayant.config.JayantProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and parses HS256 tokens. The configured secret is hashed so any length is accepted.
 * The expiration property is in seconds.
 */
@Service
public class JwtService {

    private static final Logger log = LogManager.getLogger(JwtService.class);

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(JayantProperties properties) {
        this.key = Keys.hmacShaKeyFor(sha256(properties.getJwt().getSecret()));
        this.expirationSeconds = properties.getJwt().getExpiration();
    }

    public IssuedToken create(UserPrincipal principal) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(tokenId)
                .subject(String.valueOf(principal.userId()))
                .claim("name", principal.name())
                .claim("roles", principal.roleCodes())
                .claim("functionalities", principal.functionalityCodes())
                .claim("administeredSideId", principal.administeredSideId())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, tokenId, issuedAt, expiresAt);
    }

    public Optional<UserPrincipal> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Long administeredSideId = claims.get("administeredSideId", Number.class) == null
                    ? null
                    : claims.get("administeredSideId", Number.class).longValue();
            return Optional.of(new UserPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("name", String.class),
                    strings(claims.get("roles")),
                    strings(claims.get("functionalities")),
                    administeredSideId,
                    claims.getId()));
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("Token rejected reason={}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record IssuedToken(String token, String tokenId, Instant issuedAt, Instant expiresAt) {
    }
}
