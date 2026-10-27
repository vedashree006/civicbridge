package com.civicbridge.dto;

import java.util.Map;

public record DashboardSummaryResponse(
        long totalCases,
        Map<String, Long> casesByStatus,
        long unreadNotifications
) {}
