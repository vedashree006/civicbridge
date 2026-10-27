package com.civicbridge.security;

import com.civicbridge.analytics.AnalyticsProperties;
import com.civicbridge.analytics.AnalyticsService;
import com.civicbridge.analytics.SessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bearer-JWT authentication (unchanged transport) plus server-side session validation:
 * a token is only accepted while its session id (sid) is open in the session registry, so logout and
 * revocation take effect immediately and a valid signature alone never proves a user is online.
 * Any failure leaves the request unauthenticated (fail closed) -> 401 from the entry point.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final SessionService sessionService;
    private final AnalyticsProperties props;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticate(authHeader.substring(7), request);
        } catch (Exception e) {
            // Invalid/expired token, unknown user, or a backing-store failure: stay unauthenticated.
            SecurityContextHolder.clearContext();
            log.debug("Bearer token rejected: {}", e.getClass().getSimpleName());
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String jwt, HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) return;

        Claims claims = jwtService.parseClaims(jwt); // verifies signature and expiry
        String email = claims.getSubject();
        if (email == null) return;

        String sid = claims.get("sid", String.class);
        String uidRaw = claims.get("uid", String.class);
        Long uid = null;
        if (uidRaw != null) uid = Long.valueOf(uidRaw);

        if (sid == null) {
            if (!props.isAllowLegacyTokens()) return; // pre-upgrade token: require a fresh login
        } else if (!sessionService.isActive(sid, uid)) {
            return; // logged out, revoked, expired, or unknown session
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!userDetails.isEnabled() || !email.equals(userDetails.getUsername())) return;

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        if (sid != null) {
            request.setAttribute(AnalyticsService.ATTR_SESSION_ID, sid);
            request.setAttribute(AnalyticsService.ATTR_USER_ID, uid);
            try {
                sessionService.touch(sid); // throttled; any authenticated API call counts as activity
            } catch (Exception e) {
                log.debug("Session touch failed: {}", e.getClass().getSimpleName());
            }
        }
    }
}
