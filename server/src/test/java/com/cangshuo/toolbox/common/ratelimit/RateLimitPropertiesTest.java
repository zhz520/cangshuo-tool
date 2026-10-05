package com.cangshuo.toolbox.common.ratelimit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitPropertiesTest {
    @Test void zeroValuesFallBackToDefaults() {
        var properties = new RateLimitProperties(true, 0, 0, 0, 0);
        assertEquals(60, properties.windowSeconds());
        assertEquals(600, properties.maxAnonymousRequests());
        assertEquals(3_000, properties.maxAuthenticatedRequests());
        assertEquals(30, properties.maxAuthAttempts());
    }

    @Test void invalidLimitsAreRejected() {
        assertThrows(IllegalStateException.class, () -> new RateLimitProperties(true, 3_601, 600, 3_000, 30));
        assertThrows(IllegalStateException.class, () -> new RateLimitProperties(true, -1, 600, 3_000, 30));
        assertThrows(IllegalStateException.class, () -> new RateLimitProperties(true, 60, 1_000_001, 3_000, 30));
        assertThrows(IllegalStateException.class, () -> new RateLimitProperties(true, 60, 10, 3_000, 20));
        var ok = new RateLimitProperties(false, 60, 600, 3_000, 30);
        assertFalse(ok.enabled());
    }
}
