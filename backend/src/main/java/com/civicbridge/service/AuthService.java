package com.civicbridge.service;

import com.civicbridge.analytics.AnalyticsService;
import com.civicbridge.analytics.EventType;
import com.civicbridge.analytics.SessionService;
import com.civicbridge.analytics.UserSession;
import com.civicbridge.dto.AuthResponse;
import com.civicbridge.dto.LoginRequest;
import com.civicbridge.dto.RegisterRequest;
import com.civicbridge.entity.Role;
import com.civicbridge.entity.User;
import com.civicbridge.repository.UserRepository;
import com.civicbridge.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;
    private final SessionService sessionService;
    private final AnalyticsService analyticsService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(Role.CITIZEN) // self-registration always creates CITIZEN accounts
                .enabled(true)
                .build();

        userRepository.save(user);
        auditService.log(user.getId(), "USER_REGISTERED", "User", user.getId(), user.getEmail());

        UserSession session = openSession(user);
        analyticsService.record(EventType.REGISTRATION, user.getId(), session.getId(), null, null);
        return issue(user, session);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException("User vanished after authentication"));

        auditService.log(user.getId(), "USER_LOGIN", "User", user.getId(), null);

        UserSession session = openSession(user);
        analyticsService.record(EventType.LOGIN_SUCCESS, user.getId(), session.getId(), null, null);
        return issue(user, session);
    }

    /** Ends the server-side session so the token stops working immediately. Idempotent. */
    public void logout(String sessionId) {
        sessionService.end(sessionId, "LOGOUT").ifPresent(s -> {
            auditService.log(s.getUserId(), "USER_LOGOUT", "User", s.getUserId(), null);
            analyticsService.record(EventType.LOGOUT, s.getUserId(), s.getId(), null, null);
        });
    }

    private UserSession openSession(User user) {
        return sessionService.create(user.getId(), Instant.now().plusMillis(jwtService.getExpirationMs()));
    }

    private AuthResponse issue(User user, UserSession session) {
        String token = jwtService.generateToken(toUserDetails(user), user.getRole().name(), session.getId(), user.getId());
        return new AuthResponse(token, user.getEmail(), user.getFullName(), user.getRole().name());
    }

    private UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }
}
