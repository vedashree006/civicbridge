package com.civicbridge.controller;

import com.civicbridge.analytics.AnalyticsService;
import com.civicbridge.analytics.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /** Keeps the session "recently active". Authenticated only; the session write is throttled server-side. */
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(HttpServletRequest request) {
        Object sid = request.getAttribute(AnalyticsService.ATTR_SESSION_ID);
        if (sid instanceof String s) sessionService.touch(s);
        return ResponseEntity.noContent().build();
    }
}
