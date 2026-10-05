package com.cangshuo.toolbox.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryRateLimiterTest {
    @Test void allowsUpToTheLimitWithinTheWindowAndReopensAfterwards() {
        var clock = new MutableClock();
        var limiter = new InMemoryRateLimiter(clock);
        for (int attempt = 0; attempt < 3; attempt++) {
            assertTrue(limiter.check("toolbox:rate:ip:1.2.3.4", 3, 60).allowed());
        }
        var denied = limiter.check("toolbox:rate:ip:1.2.3.4", 3, 60);
        assertFalse(denied.allowed());
        assertEquals(0, denied.remaining());
        assertEquals(60, denied.retryAfterSeconds());
        clock.advance(Duration.ofSeconds(61));
        assertTrue(limiter.check("toolbox:rate:ip:1.2.3.4", 3, 60).allowed());
    }

    @Test void keysAreIndependentAndRemainingCountsDown() {
        var limiter = new InMemoryRateLimiter(new MutableClock());
        assertEquals(2, limiter.check("a", 3, 60).remaining());
        assertEquals(1, limiter.check("a", 3, 60).remaining());
        assertTrue(limiter.check("b", 1, 60).allowed());
        assertFalse(limiter.check("b", 1, 60).allowed());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-05T08:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }
}
