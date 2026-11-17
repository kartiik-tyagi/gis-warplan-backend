package com.drdo.jayant.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.drdo.jayant.security.UserPrincipal;
import com.drdo.jayant.service.AccessService;
import com.drdo.jayant.service.AuthService;
import com.drdo.jayant.service.AuthService.LoginResult;
import com.drdo.jayant.service.AuthService.MeResponse;

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
}
