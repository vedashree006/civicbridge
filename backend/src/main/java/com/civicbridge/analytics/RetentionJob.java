package com.civicbridge.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Expires abandoned sessions and deletes old analytics data per the configured retention periods. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetentionJob {

    private final UserSessionRepository sessions;
    private final AnalyticsEventRepository events;
    private final ConsentRecordRepository consents;
    private final AnalyticsProperties props;

    /** Sessions whose JWT lifetime has passed (token expiry, closed browser, abandoned tab). */
    @Scheduled(fixedDelayString = "${app.analytics.expire-sweep-ms:60000}")
    @Transactional
    public void expireSessions() {
        try {
            int n = sessions.markExpired(Instant.now());
            if (n > 0) log.debug("Marked {} sessions expired", n);
        } catch (Exception e) {
            log.warn("Session expiry sweep failed: {}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "${app.analytics.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void cleanup() {
        try {
            Instant now = Instant.now();
            int e = events.deleteOlderThan(now.minus(props.getEventRetentionDays(), ChronoUnit.DAYS));
            int s = sessions.deleteEndedBefore(now.minus(props.getSessionRetentionDays(), ChronoUnit.DAYS));
            int c = consents.deleteOlderThan(now.minus(props.getConsentRetentionDays(), ChronoUnit.DAYS));
            log.info("Retention cleanup removed events={} sessions={} consentRecords={}", e, s, c);
        } catch (Exception ex) {
            log.warn("Retention cleanup failed: {}", ex.getClass().getSimpleName());
        }
    }
}
