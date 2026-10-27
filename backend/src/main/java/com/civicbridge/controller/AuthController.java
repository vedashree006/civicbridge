package com.civicbridge.controller;

import com.civicbridge.analytics.AnalyticsService;
import com.civicbridge.dto.AuthResponse;
import com.civicbridge.dto.LoginRequest;
import com.civicbridge.dto.RegisterRequest;
import com.civicbridge.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Idempotent: always 204, even without/with an already-invalid token. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Object sid = request.getAttribute(AnalyticsService.ATTR_SESSION_ID);
        authService.logout(sid instanceof String s ? s : null);
        return ResponseEntity.noContent().build();
    }
}
