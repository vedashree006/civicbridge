package com.civicbridge.controller;

import com.civicbridge.analytics.AnalyticsDtos;
import com.civicbridge.analytics.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Aggregate-only analytics for administrators. /api/admin/** is restricted to ROLE_ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
public class AnalyticsAdminController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsDtos.Overview> overview() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(analyticsService.overview());
    }

    @GetMapping("/trends")
    public ResponseEntity<AnalyticsDtos.Trends> trends(@RequestParam(defaultValue = "daily") String granularity,
                                                       @RequestParam(required = false) Integer periods) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(analyticsService.trends(granularity, periods));
    }
}
