package com.cangshuo.toolbox.storage;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoragePropertiesTest {
    @Test void disabledStorageUsesSafeDefaults() {
        var properties = new StorageProperties(false, null, null, null, null, 0, 0, null);
        assertFalse(properties.enabled());
        assertEquals("http://127.0.0.1:9000", properties.endpoint());
        assertEquals("toolbox-files", properties.bucket());
        assertEquals(900, properties.presignedExpirySeconds());
        assertEquals(26_214_400L, properties.maxObjectBytes());
        assertEquals(List.of("image/png", "image/jpeg", "image/webp", "application/pdf", "text/plain"),
                properties.allowedContentTypes());
    }

    @Test void enabledStorageRequiresCredentialsAndValidNames() {
        assertThrows(IllegalStateException.class, () -> new StorageProperties(true, "http://minio:9000", null,
                "secret", "toolbox-files", 900, 1024, null));
        assertThrows(IllegalStateException.class, () -> new StorageProperties(true, "http://minio:9000", "key",
                null, "toolbox-files", 900, 1024, null));
        assertThrows(IllegalStateException.class, () -> new StorageProperties(true, "http://minio:9000", "key",
                "secret", "Bad_Bucket", 900, 1024, null));
        var ok = new StorageProperties(true, "http://minio:9000/", "key", "secret",
                "toolbox-files", 60, 1, List.of("IMAGE/PNG"));
        assertEquals("http://minio:9000", ok.endpoint());
        assertEquals(List.of("image/png"), ok.allowedContentTypes());
    }

    @Test void boundsAndEmptyContentTypesAreRejected() {
        assertThrows(IllegalStateException.class, () -> new StorageProperties(false, null, null, null, null,
                10, 1024, null));
        assertThrows(IllegalStateException.class, () -> new StorageProperties(false, null, null, null, null,
                604_801, 1024, null));
        assertThrows(IllegalStateException.class, () -> new StorageProperties(false, null, null, null, null,
                900, 104_857_601L, null));
        assertThrows(IllegalStateException.class, () -> new StorageProperties(false, null, null, null, null,
                900, 1024, List.of(" ", "")));
    }
}
