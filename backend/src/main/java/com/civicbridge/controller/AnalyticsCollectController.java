package com.civicbridge.controller;

import com.civicbridge.analytics.AnalyticsDtos;
import com.civicbridge.analytics.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Browser-submitted page views. Only PAGE_VIEW can be reported by clients; every other event type is
 * generated server-side. The browser sends it only after analytics consent and signals this with a header;
 * the server cannot verify consent itself, so this is a documented trust boundary.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsCollectController {

    private final AnalyticsService analyticsService;

    @PostMapping("/collect")
    public ResponseEntity<Void> collect(@RequestHeader(value = "X-Analytics-Consent", required = false) String consent,
                                        @RequestBody AnalyticsDtos.CollectRequest body,
                                        HttpServletRequest request) {
        if (!"granted".equals(consent)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Object uid = request.getAttribute(AnalyticsService.ATTR_USER_ID);
        Object sid = request.getAttribute(AnalyticsService.ATTR_SESSION_ID);
        var result = analyticsService.collectPageView(body.path(), body.visitorId(),
                uid instanceof Long l ? l : null, sid instanceof String s ? s : null);
        return switch (result) {
            case STORED, DUPLICATE -> ResponseEntity.noContent().build();
            case RATE_LIMITED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            case INVALID -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        };
    }
}
