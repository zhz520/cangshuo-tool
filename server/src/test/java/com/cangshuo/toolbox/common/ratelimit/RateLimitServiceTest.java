package com.cangshuo.toolbox.common.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitServiceTest {
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final InMemoryRateLimiter fallback = new InMemoryRateLimiter(
            Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ZoneOffset.UTC));
    private final RateLimitService service = new RateLimitService(redis, fallback);

    @Test void redisCountersDriveTheDecisionAndTtlDrivesRetryAfter() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment("toolbox:rate:ip:1.2.3.4")).thenReturn(1L, 4L);
        when(redis.getExpire("toolbox:rate:ip:1.2.3.4")).thenReturn(42L);
        var first = service.check("toolbox:rate:ip:1.2.3.4", 3, 60);
        assertTrue(first.allowed());
        verify(redis).expire(eq("toolbox:rate:ip:1.2.3.4"), any());
        var denied = service.check("toolbox:rate:ip:1.2.3.4", 3, 60);
        assertFalse(denied.allowed());
        assertEquals(42, denied.retryAfterSeconds());
    }

    @Test void redisFailuresFallBackToTheInMemoryWindow() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        assertTrue(service.check("toolbox:rate:ip:5.6.7.8", 2, 60).allowed());
        assertTrue(service.check("toolbox:rate:ip:5.6.7.8", 2, 60).allowed());
        assertFalse(service.check("toolbox:rate:ip:5.6.7.8", 2, 60).allowed());
    }
}
