package com.civicbridge.analytics;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    @Query("select count(s) from UserSession s where s.endedAt is null and s.expiresAt > :now")
    long countOpen(@Param("now") Instant now);

    @Query("select count(distinct s.userId) from UserSession s where s.endedAt is null and s.expiresAt > :now")
    long countDistinctOpenUsers(@Param("now") Instant now);

    @Query("select count(distinct s.userId) from UserSession s where s.endedAt is null and s.expiresAt > :now and s.lastSeenAt >= :since")
    long countDistinctActiveUsers(@Param("now") Instant now, @Param("since") Instant since);

    @Query("select count(s) from UserSession s where s.endedAt is null and s.expiresAt > :now and s.lastSeenAt >= :since")
    long countActiveSessions(@Param("now") Instant now, @Param("since") Instant since);

    @Query("select count(distinct s.userId) from UserSession s where s.lastSeenAt >= :since")
    long countDistinctUsersSeenSince(@Param("since") Instant since);

    /** Conditional update = DB-level write throttle (only touches rows not seen since :threshold). */
    @Modifying
    @Query("update UserSession s set s.lastSeenAt = :now where s.id = :id and s.endedAt is null and s.lastSeenAt < :threshold")
    int touch(@Param("id") String id, @Param("now") Instant now, @Param("threshold") Instant threshold);

    @Modifying
    @Query("update UserSession s set s.endedAt = :now, s.endReason = 'EXPIRED' where s.endedAt is null and s.expiresAt <= :now")
    int markExpired(@Param("now") Instant now);

    @Modifying
    @Query("delete from UserSession s where s.endedAt is not null and s.endedAt < :cutoff")
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
