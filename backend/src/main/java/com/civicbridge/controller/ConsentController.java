package com.civicbridge.controller;

import com.civicbridge.analytics.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** Stores an accountability record of a consent decision (no IP, no user agent, no visitor id). */
@RestController
@RequestMapping("/api/consent")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentRecordRepository repository;
    private final AnalyticsProperties props;
    private final SimpleRateLimiter limiter;

    @PostMapping
    public ResponseEntity<Void> record(@RequestBody AnalyticsDtos.ConsentRequest body) {
        if (body.policyVersion() == null || !body.policyVersion().equals(props.getConsentPolicyVersion())
                || body.marketing()) { // no marketing functionality exists, so it can never be granted
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!limiter.allow("consent:global", 600, 60_000)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        Long userId = null;
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            Object v = attrs.getAttribute(AnalyticsService.ATTR_USER_ID,
                    org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            if (v instanceof Long l) userId = l;
        }
        try {
            repository.save(ConsentRecord.builder().userId(userId).policyVersion(body.policyVersion())
                    .preferences(body.preferences()).analytics(body.analytics()).marketing(false)
                    .recordedAt(Instant.now()).build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build(); // the browser decision still applies locally
        }
        return ResponseEntity.noContent().build();
    }
}
