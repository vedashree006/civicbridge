package com.civicbridge.analytics;

import com.civicbridge.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AnalyticsService {

    public static final String ATTR_SESSION_ID = "cb.sessionId";
    public static final String ATTR_USER_ID = "cb.userId";

    private static final Pattern PATH = Pattern.compile("^/[A-Za-z0-9_\\-/]{0,150}$");
    private static final Pattern UUID_RE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Set<String> KNOWN_ROOTS = Set.of("login", "register", "dashboard", "cases", "admin", "privacy");

    private final AnalyticsEventRepository events;
    private final UserSessionRepository sessions;
    private final UserRepository users;
    private final SessionService sessionService;
    private final AnalyticsProperties props;
    private final SimpleRateLimiter limiter;
    private final String salt;

    public AnalyticsService(AnalyticsEventRepository events, UserSessionRepository sessions, UserRepository users,
                            SessionService sessionService, AnalyticsProperties props, SimpleRateLimiter limiter) {
        this.events = events;
        this.sessions = sessions;
        this.users = users;
        this.sessionService = sessionService;
        this.props = props;
        this.limiter = limiter;
        if (props.getHashSalt() == null || props.getHashSalt().isBlank()) {
            byte[] b = new byte[32];
            new SecureRandom().nextBytes(b);
            this.salt = HexFormat.of().formatHex(b);
            log.warn("ANALYTICS_HASH_SALT not set: using a random per-process salt (visitor counts reset on restart)");
        } else {
            this.salt = props.getHashSalt();
        }
    }

    // ---------------------------------------------------------------- recording

    /** Records a server-generated event for the current authenticated request. Never throws. */
    public void recordForCurrentRequest(EventType type, Long userId) {
        record(type, userId, currentSessionId(), null, null);
    }

    /** Records an event. Analytics must never break business flows, so failures are swallowed and logged. */
    public void record(EventType type, Long userId, String sessionId, String path, String visitorHash) {
        try {
            events.save(AnalyticsEvent.builder()
                    .eventType(type).userId(userId).sessionId(sessionId)
                    .path(path).visitorHash(visitorHash).occurredAt(Instant.now()).build());
        } catch (Exception e) {
            log.warn("Analytics event {} dropped: {}", type, e.getClass().getSimpleName());
        }
    }

    public enum CollectResult { STORED, DUPLICATE, RATE_LIMITED, INVALID }

    /** Validated, de-duplicated, rate-limited page-view ingestion (browser-submitted, consent-gated by caller). */
    public CollectResult collectPageView(String rawPath, String visitorId, Long userId, String sessionId) {
        String path = normalizePath(rawPath);
        if (path == null || visitorId == null || !UUID_RE.matcher(visitorId).matches()) return CollectResult.INVALID;
        String hash = visitorHash(visitorId);
        String who = sessionId != null ? sessionId : hash;
        if (!limiter.allow("collect:" + who, props.getCollectPerMinute(), 60_000)) return CollectResult.RATE_LIMITED;
        if (!limiter.allow("dup:" + hash + path, 1, 5_000)) return CollectResult.DUPLICATE;
        record(EventType.PAGE_VIEW, userId, sessionId, path, hash);
        return CollectResult.STORED;
    }

    /** Only a short allow-list of route roots is kept; numeric ids become :id. Returns null if invalid. */
    static String normalizePath(String raw) {
        if (raw == null || !PATH.matcher(raw).matches()) return null;
        String[] parts = raw.split("/");
        if (parts.length <= 1) return "/";
        if (!KNOWN_ROOTS.contains(parts[1])) return "/other";
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length && i <= 3; i++) {
            sb.append('/').append(parts[i].matches("\\d+") ? ":id" : (i == 1 ? parts[i] : (parts[i].length() > 30 ? ":x" : parts[i])));
        }
        return sb.toString();
    }

    /** SHA-256(salt : local date : visitorId). Rotates daily, so visitors cannot be followed across days. No IP, no UA. */
    String visitorHash(String visitorId) {
        String day = LocalDate.now(ZoneId.of(props.getZone())).toString();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((salt + ":" + day + ":" + visitorId).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String currentSessionId() {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            Object v = req.getAttribute(ATTR_SESSION_ID);
            return v instanceof String s ? s : null;
        }
        return null;
    }

    // ---------------------------------------------------------------- reading

    public AnalyticsDtos.Overview overview() {
        ZoneId zone = ZoneId.of(props.getZone());
        Instant now = Instant.now();
        Instant activeSince = now.minusSeconds(props.getActiveWindowSeconds());
        Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();

        return new AnalyticsDtos.Overview(
                now,
                props.getActiveWindowSeconds(),
                sessionService.redisAvailable(),
                users.count(),
                sessions.countDistinctOpenUsers(now),
                sessions.countOpen(now),
                sessions.countActiveSessions(now, activeSince),
                sessions.countDistinctActiveUsers(now, activeSince),
                events.countDistinctActingUsersSince(activeSince),
                sessions.countDistinctUsersSeenSince(startOfToday),
                events.countDistinctAnonymousVisitorsSince(startOfToday),
                countsSince(startOfToday),
                countsSince(now.minus(7, ChronoUnit.DAYS)),
                countsSince(now.minus(30, ChronoUnit.DAYS)));
    }

    private Map<String, Long> countsSince(Instant since) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (EventType t : EventType.values()) out.put(t.name(), 0L);
        for (Object[] row : events.countByTypeSince(since)) {
            out.put(((EventType) row[0]).name(), (Long) row[1]);
        }
        return out;
    }

    public AnalyticsDtos.Trends trends(String granularity, Integer periods) {
        String g = granularity == null ? "daily" : granularity.toLowerCase(Locale.ROOT);
        int max, def;
        switch (g) {
            case "daily" -> { max = 90; def = 14; }
            case "weekly" -> { max = 13; def = 8; }
            case "monthly" -> { max = 3; def = 3; }
            default -> throw new IllegalArgumentException("granularity must be daily, weekly or monthly");
        }
        int n = periods == null ? def : Math.max(1, Math.min(periods, max));
        ZoneId zone = ZoneId.of(props.getZone());
        LocalDate today = LocalDate.now(zone);

        LinkedHashMap<LocalDate, long[]> counts = new LinkedHashMap<>();
        Map<LocalDate, Set<Long>> usersBy = new HashMap<>();
        Map<LocalDate, Set<String>> visitorsBy = new HashMap<>();
        LocalDate first = bucketStart(g, today);
        for (int i = n - 1; i >= 0; i--) {
            LocalDate b = switch (g) {
                case "daily" -> first.minusDays(i);
                case "weekly" -> first.minusWeeks(i);
                default -> first.minusMonths(i);
            };
            counts.put(b, new long[7]);
            usersBy.put(b, new HashSet<>());
            visitorsBy.put(b, new HashSet<>());
        }
        LocalDate earliest = counts.keySet().iterator().next();

        for (Object[] row : events.findForTrends(earliest.atStartOfDay(zone).toInstant())) {
            EventType type = (EventType) row[0];
            LocalDate key = bucketStart(g, ((Instant) row[1]).atZone(zone).toLocalDate());
            long[] c = counts.get(key);
            if (c == null) continue;
            switch (type) {
                case LOGIN_SUCCESS -> c[0]++;
                case LOGOUT -> c[1]++;
                case CASE_SUBMITTED -> c[2]++;
                case DOCUMENT_UPLOADED -> c[3]++;
                case AI_GUIDANCE_USED -> c[4]++;
                case PAGE_VIEW -> c[5]++;
                default -> { }
            }
            if (type != EventType.PAGE_VIEW && row[2] != null) usersBy.get(key).add((Long) row[2]);
            if (type == EventType.PAGE_VIEW && row[2] == null && row[3] != null) visitorsBy.get(key).add((String) row[3]);
        }

        List<AnalyticsDtos.TrendPoint> points = new ArrayList<>();
        counts.forEach((k, c) -> points.add(new AnalyticsDtos.TrendPoint(k.toString(), c[0], c[1], c[2], c[3], c[4], c[5],
                usersBy.get(k).size(), visitorsBy.get(k).size())));
        return new AnalyticsDtos.Trends(g, props.getZone(), points);
    }

    private static LocalDate bucketStart(String g, LocalDate d) {
        return switch (g) {
            case "weekly" -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "monthly" -> d.withDayOfMonth(1);
            default -> d;
        };
    }
}
