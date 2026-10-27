package com.civicbridge.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    /** Each field is a distinct metric - see docs/ANALYTICS_METRICS in the report for definitions. */
    public record Overview(
            Instant generatedAt,
            long activeWindowSeconds,
            boolean redisAvailable,
            long registeredUsers,
            long authenticatedUsers,        // distinct users with >=1 open, unexpired session
            long openSessions,              // open, unexpired sessions (any recency)
            long activeSessions,            // open sessions seen within the active window
            long activeUsers,               // distinct users behind activeSessions
            long recentlyActedUsers,        // distinct users with a recorded action in the window
            long uniqueAuthenticatedUsersToday, // distinct users whose session was seen today
            long anonymousVisitorsToday,    // consented, logged-out visitors (daily-rotating hash)
            Map<String, Long> today,
            Map<String, Long> last7Days,
            Map<String, Long> last30Days) {}

    public record TrendPoint(String bucketStart, long logins, long logouts, long caseSubmissions,
                             long documentUploads, long aiGuidanceUses, long pageViews,
                             long uniqueActiveUsers, long visitorDays) {}

    public record Trends(String granularity, String zone, List<TrendPoint> points) {}

    public record CollectRequest(String path, String visitorId) {}

    public record ConsentRequest(String policyVersion, boolean preferences, boolean analytics, boolean marketing) {}
}
