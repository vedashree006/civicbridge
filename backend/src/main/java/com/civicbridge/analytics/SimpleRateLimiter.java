package com.civicbridge.analytics;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny fixed-window in-memory limiter. Per-instance only (documented limitation).
 * Bounded: the map is cleared if it grows beyond MAX_KEYS so it cannot be used for memory exhaustion.
 */
@Component
public class SimpleRateLimiter {
    private static final int MAX_KEYS = 50_000;
    private record Window(long startMs, int count) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_KEYS) windows.clear();
        boolean[] allowed = {false};
        windows.compute(key, (k, w) -> {
            if (w == null || now - w.startMs() >= windowMs) {
                allowed[0] = limit > 0;
                return new Window(now, 1);
            }
            if (w.count() < limit) { allowed[0] = true; return new Window(w.startMs(), w.count() + 1); }
            return w;
        });
        return allowed[0];
    }
}
