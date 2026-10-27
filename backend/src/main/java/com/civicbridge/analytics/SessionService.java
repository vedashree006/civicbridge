package com.civicbridge.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side session registry. A JWT alone never proves a user is online: every token carries a
 * session id (sid) that must be open in this registry.
 *  - PostgreSQL (user_sessions) is the source of truth.
 *  - Redis holds short-lived validity cache keys and write-throttle keys. If Redis is down,
 *    everything falls back to PostgreSQL (slower but correct). If PostgreSQL is down, validation
 *    throws and the caller fails closed (request is not authenticated).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private static final String VALID_KEY = "cb:session:valid:";
    private static final String SEEN_KEY = "cb:session:seen:";

    private final UserSessionRepository sessions;
    private final StringRedisTemplate redis;
    private final AnalyticsProperties props;

    @Transactional
    public UserSession create(Long userId, Instant expiresAt) {
        Instant now = Instant.now();
        UserSession s = UserSession.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .createdAt(now)
                .lastSeenAt(now)
                .expiresAt(expiresAt)
                .build();
        sessions.save(s);
        cacheValid(s.getId(), userId, expiresAt);
        return s;
    }

    /** True when the session exists, belongs to the user, is not ended and has not expired. */
    public boolean isActive(String sid, Long userId) {
        if (sid == null || userId == null) return false;
        try {
            String cached = redis.opsForValue().get(VALID_KEY + sid);
            if (cached != null) return cached.equals(String.valueOf(userId));
        } catch (Exception e) {
            log.warn("Redis unavailable during session validation; falling back to database");
        }
        Optional<UserSession> found = sessions.findById(sid);
        boolean ok = found.isPresent()
                && found.get().getEndedAt() == null
                && found.get().getUserId().equals(userId)
                && found.get().getExpiresAt().isAfter(Instant.now());
        if (ok) cacheValid(sid, userId, found.get().getExpiresAt());
        return ok;
    }

    /** Records activity. Persisted at most once per touch-interval per session. */
    @Transactional
    public void touch(String sid) {
        long interval = props.getTouchIntervalSeconds();
        try {
            Boolean first = redis.opsForValue().setIfAbsent(SEEN_KEY + sid, "1", Duration.ofSeconds(interval));
            if (Boolean.FALSE.equals(first)) return; // already recorded within the interval
        } catch (Exception e) {
            // Redis down: the conditional UPDATE below still throttles writes in the database.
        }
        Instant now = Instant.now();
        sessions.touch(sid, now, now.minusSeconds(interval));
    }

    /** Ends a session (logout/revocation). Returns the session if it was open. */
    @Transactional
    public Optional<UserSession> end(String sid, String reason) {
        if (sid == null) return Optional.empty();
        Optional<UserSession> found = sessions.findById(sid);
        try { redis.delete(VALID_KEY + sid); redis.delete(SEEN_KEY + sid); } catch (Exception ignored) { }
        if (found.isEmpty() || found.get().getEndedAt() != null) return Optional.empty();
        UserSession s = found.get();
        s.setEndedAt(Instant.now());
        s.setEndReason(reason);
        sessions.save(s);
        return Optional.of(s);
    }

    private void cacheValid(String sid, Long userId, Instant expiresAt) {
        try {
            long ttl = Math.min(props.getSessionCacheSeconds(),
                    Math.max(1, Duration.between(Instant.now(), expiresAt).getSeconds()));
            redis.opsForValue().set(VALID_KEY + sid, String.valueOf(userId), Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.debug("Redis unavailable; session cache skipped");
        }
    }

    public boolean redisAvailable() {
        try {
            return redis.getConnectionFactory() != null
                    && redis.getConnectionFactory().getConnection().ping() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
