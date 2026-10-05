package com.cangshuo.toolbox.storage;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageQuotaServiceTest {
    private final StoredObjectRepository objects = mock(StoredObjectRepository.class);
    private final StorageQuotaService service = new StorageQuotaService(objects,
            new QuotaProperties(true, 100, 3, 1000));

    @Test void allowsUploadsWithinOwnerAndGlobalLimits() {
        when(objects.usage("ADMIN", 7)).thenReturn(new StoredObjectRepository.Usage(60, 2));
        when(objects.totalBytes()).thenReturn(500L);
        service.check("ADMIN", 7, 40);
    }

    @Test void rejectsOwnerBytesOwnerObjectsAndGlobalLimits() {
        when(objects.totalBytes()).thenReturn(500L);
        when(objects.usage("ADMIN", 7)).thenReturn(new StoredObjectRepository.Usage(80, 1));
        assertEquals(ApiError.QUOTA_EXCEEDED,
                assertThrows(ApiException.class, () -> service.check("ADMIN", 7, 21)).error());
        when(objects.usage("ADMIN", 7)).thenReturn(new StoredObjectRepository.Usage(10, 3));
        assertEquals(ApiError.QUOTA_EXCEEDED,
                assertThrows(ApiException.class, () -> service.check("ADMIN", 7, 1)).error());
        when(objects.usage("ADMIN", 7)).thenReturn(new StoredObjectRepository.Usage(10, 1));
        when(objects.totalBytes()).thenReturn(990L);
        assertEquals(ApiError.QUOTA_EXCEEDED,
                assertThrows(ApiException.class, () -> service.check("ADMIN", 7, 11)).error());
    }

    @Test void disabledQuotaSkipsAccountingAndSnapshotCarriesLimits() {
        var disabled = new StorageQuotaService(objects, new QuotaProperties(false, 100, 3, 1000));
        disabled.check("ADMIN", 7, 10_000);
        verifyNoInteractions(objects);
        when(objects.usage("ADMIN", 7)).thenReturn(new StoredObjectRepository.Usage(12, 2));
        when(objects.totalBytes()).thenReturn(34L);
        var snapshot = service.snapshot("ADMIN", 7);
        assertTrue(snapshot.enabled());
        assertEquals(12, snapshot.ownerBytes());
        assertEquals(2, snapshot.ownerObjects());
        assertEquals(34, snapshot.totalBytes());
        assertEquals(100, snapshot.maxOwnerBytes());
        assertEquals(3, snapshot.maxOwnerObjects());
        assertEquals(1000, snapshot.maxTotalBytes());
    }
}
