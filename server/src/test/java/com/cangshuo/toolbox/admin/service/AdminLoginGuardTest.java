package com.cangshuo.toolbox.admin.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminLoginGuardTest {
    @Test void locksAfterFiveFailuresAndReopensAfterFifteenMinutes() {
        var clock = new MutableClock();
        var guard = new AdminLoginGuard(clock);
        for (int i = 0; i < 4; i++) guard.recordFailure("u:root");
        assertFalse(guard.locked("u:root"));
        guard.recordFailure("u:root");
        assertTrue(guard.locked("u:root"));
        clock.advance(Duration.ofMinutes(14));
        assertTrue(guard.locked("u:root"));
        clock.advance(Duration.ofMinutes(1));
        assertFalse(guard.locked("u:root"));
        guard.recordFailure("u:root");
        assertFalse(guard.locked("u:root"));
        for (int i = 0; i < 4; i++) guard.recordFailure("u:root");
        assertTrue(guard.locked("u:root"));
    }

    @Test void keysAreIndependentAndClearReleasesCounters() {
        var guard = new AdminLoginGuard(new MutableClock());
        for (int i = 0; i < 5; i++) guard.recordFailure("i:203.0.113.7");
        assertTrue(guard.locked("i:203.0.113.7"));
        assertFalse(guard.locked("i:203.0.113.8"));
        guard.clear("i:203.0.113.7");
        assertFalse(guard.locked("i:203.0.113.7"));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-05T08:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }
}
