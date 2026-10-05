package com.cangshuo.toolbox.common.ratelimit;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Redis-backed limiter with an in-memory fallback so a Redis outage never blocks the API. */
@Service
public class RateLimitService {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitService.class);
    private static final long FAILURE_LOG_INTERVAL_MILLIS = 60_000L;
    private final StringRedisTemplate redis;
    private final InMemoryRateLimiter fallback;
    private final AtomicLong lastFailureLog = new AtomicLong();

    public RateLimitService(StringRedisTemplate redis, InMemoryRateLimiter fallback) {
        this.redis = redis; this.fallback = fallback;
    }

    public RateLimitDecision check(String key, int limit, int windowSeconds) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) throw new IllegalStateException("Redis counter unavailable");
            if (count == 1L) redis.expire(key, Duration.ofSeconds(windowSeconds));
            Long ttl = redis.getExpire(key);
            int retryAfter = ttl == null || ttl < 1 ? windowSeconds : (int) Math.min(ttl, windowSeconds);
            return new RateLimitDecision(count <= limit, limit, (int) Math.max(0, limit - count), retryAfter);
        } catch (Exception exception) {
            logFallback(exception);
            return fallback.check(key, limit, windowSeconds);
        }
    }

    private void logFallback(Exception exception) {
        long now = System.currentTimeMillis();
        long previous = lastFailureLog.get();
        if (now - previous < FAILURE_LOG_INTERVAL_MILLIS || !lastFailureLog.compareAndSet(previous, now)) return;
        LOG.warn("Rate limit counter fell back to memory; exceptionType={}", exception.getClass().getName());
    }
}
