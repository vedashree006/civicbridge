package com.civicbridge.analytics;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Bound from app.analytics.* (see application.yml). All values are overridable by environment variables. */
@Component
@ConfigurationProperties(prefix = "app.analytics")
@Getter
@Setter
public class AnalyticsProperties {
    /** A session counts as "active" if it was seen within this many seconds. Default five minutes. */
    private long activeWindowSeconds = 300;
    /** Minimum gap between persisted last-seen updates for one session (limits DB writes). */
    private long touchIntervalSeconds = 30;
    /** How long a "session is valid" answer is cached in Redis before DB re-validation. */
    private long sessionCacheSeconds = 60;
    /** Raw analytics events are deleted after this many days. */
    private int eventRetentionDays = 90;
    /** Ended/expired session rows are deleted this many days after they ended. */
    private int sessionRetentionDays = 30;
    /** Consent records are kept for accountability this long (legal review required; see docs). */
    private int consentRetentionDays = 1095;
    /** Calendar zone used for "today", daily/weekly/monthly buckets. */
    private String zone = "Europe/Berlin";
    /** Secret salt for daily visitor hashes. If blank a random per-process salt is generated. */
    private String hashSalt = "";
    /** Accept legacy JWTs that carry no session id (issued before this upgrade). Off by default. */
    private boolean allowLegacyTokens = false;
    /** Max page-view reports per visitor/session per minute. */
    private int collectPerMinute = 60;
    /** Current cookie/consent policy version. Bumping it re-prompts every visitor. */
    private String consentPolicyVersion = "2026-10-1";
}
