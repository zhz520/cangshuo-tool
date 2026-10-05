package com.cangshuo.toolbox.common.ratelimit;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Single-instance fixed-window counter, also used as the fallback when Redis is unavailable. */
@Component
public class InMemoryRateLimiter {
    private static final int PRUNE_THRESHOLD = 10_000;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(Clock clock) { this.clock = clock; }

    public RateLimitDecision check(String key, int limit, int windowSeconds) {
        long now = clock.millis();
        long windowMillis = windowSeconds * 1000L;
        Window window = windows.compute(key, (ignored, existing) ->
                existing == null || now - existing.startMillis >= windowMillis ? new Window(now) : existing);
        int used = window.count.incrementAndGet();
        long elapsed = now - window.startMillis;
        int retryAfter = (int) Math.max(1, (windowMillis - elapsed + 999) / 1000);
        if (windows.size() > PRUNE_THRESHOLD) {
            windows.values().removeIf(candidate -> now - candidate.startMillis >= windowMillis);
        }
        return new RateLimitDecision(used <= limit, limit, Math.max(0, limit - used), retryAfter);
    }

    private static final class Window {
        private final long startMillis;
        private final AtomicInteger count = new AtomicInteger();
        private Window(long startMillis) { this.startMillis = startMillis; }
    }
}
