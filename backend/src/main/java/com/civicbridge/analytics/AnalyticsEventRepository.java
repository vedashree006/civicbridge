package com.civicbridge.analytics;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    /** Anonymous (not logged in) consented visitors: distinct daily-rotating hashes. */
    @Query("select count(distinct e.visitorHash) from AnalyticsEvent e where e.eventType = 'PAGE_VIEW' and e.userId is null and e.visitorHash is not null and e.occurredAt >= :since")
    long countDistinctAnonymousVisitorsSince(@Param("since") Instant since);

    /** Users who performed a server-recorded action (login, case, upload, AI) - excludes page views. */
    @Query("select count(distinct e.userId) from AnalyticsEvent e where e.userId is not null and e.eventType <> 'PAGE_VIEW' and e.occurredAt >= :since")
    long countDistinctActingUsersSince(@Param("since") Instant since);

    @Query("select e.eventType, e.occurredAt, e.userId, e.visitorHash from AnalyticsEvent e where e.occurredAt >= :since")
    List<Object[]> findForTrends(@Param("since") Instant since);

    @Query("select e.eventType, count(e) from AnalyticsEvent e where e.occurredAt >= :since group by e.eventType")
    List<Object[]> countByTypeSince(@Param("since") Instant since);

    @Modifying
    @Query("delete from AnalyticsEvent e where e.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
