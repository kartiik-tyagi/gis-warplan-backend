package com.gis.warplan.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gis.warplan.domain.Theme;
import com.gis.warplan.security.UserPrincipal;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.AuthService;
import com.gis.warplan.service.AuthService.LoginContext;
import com.gis.warplan.service.AuthService.LoginResult;
import com.gis.warplan.service.AuthService.LoginUser;
import com.gis.warplan.service.AuthService.MeResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AccessService access;

    public AuthController(AuthService authService, AccessService access) {
        this.authService = authService;
        this.access = access;
    }

    @GetMapping("/login-context")
    public LoginContextResponse loginContext() {
        LoginContext context = authService.loginContext();
        return LoginContextResponse.of(context);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String name = firstNonBlank(request.name(), request.username());
        if (name == null) {
            throw new BadRequestException("Name is required.");
        }
        LoginResult result = authService.login(name.trim(), request.password());
        return new LoginResponse(result.token(), "Bearer", result.expiresAt().toString(), result.me());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        UserPrincipal principal = access.requirePrincipal();
        authService.logout(principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me() {
        return authService.me(access.requirePrincipal());
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    public record LoginRequest(String name, String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, String tokenType, String expiresAt, MeResponse me) {
    }

    public record LoginContextResponse(
            String applicationName,
            String exerciseTitle,
            List<LoginUser> activeUsers,
            ThemeResponse loginTheme,
            String logoUrl,
            String faviconUrl,
            String loginBackgroundUrl) {

        static LoginContextResponse of(LoginContext context) {
            return new LoginContextResponse(
                    context.applicationName(),
                    context.exerciseTitle(),
                    context.activeUsers(),
                    ThemeResponse.of(context.loginTheme()),
                    context.logoUrl(),
                    context.faviconUrl(),
                    context.loginBackgroundUrl());
        }
    }

    public record ThemeResponse(
            Long id,
            String name,
            String description,
            boolean defaultTheme,
            Map<String, Object> configJson,
            Instant createdAt,
            Instant updatedAt) {

        static ThemeResponse of(Theme theme) {
            return new ThemeResponse(theme.getId(), theme.getName(), theme.getDescription(), theme.isDefaultTheme(),
                    theme.getConfigJson(), theme.getCreatedAt(), theme.getUpdatedAt());
        }
    }
}
