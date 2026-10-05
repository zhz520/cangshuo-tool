package com.cangshuo.toolbox.storage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuotaPropertiesTest {
    @Test void zeroValuesFallBackToDefaults() {
        var properties = new QuotaProperties(true, 0, 0, 0);
        assertEquals(QuotaProperties.DEFAULT_MAX_OWNER_BYTES, properties.maxOwnerBytes());
        assertEquals(QuotaProperties.DEFAULT_MAX_OWNER_OBJECTS, properties.maxOwnerObjects());
        assertEquals(QuotaProperties.DEFAULT_MAX_TOTAL_BYTES, properties.maxTotalBytes());
    }

    @Test void invalidLimitsAreRejected() {
        assertThrows(IllegalStateException.class, () -> new QuotaProperties(true, -1, 10, 100));
        assertThrows(IllegalStateException.class, () -> new QuotaProperties(true, 1000, 10, 100));
        assertThrows(IllegalStateException.class, () -> new QuotaProperties(true, 0, 0,
                QuotaProperties.MAX_ALLOWED_TOTAL_BYTES + 1));
        var ok = new QuotaProperties(false, 100, 3, 1000);
        assertFalse(ok.enabled());
        assertEquals(100, ok.maxOwnerBytes());
    }
}
