package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.storage.ObjectStorage;
import com.cangshuo.toolbox.storage.StorageProperties;
import com.cangshuo.toolbox.storage.StorageUnavailableException;
import com.cangshuo.toolbox.storage.StorageQuotaService;
import com.cangshuo.toolbox.storage.StoredObjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminStorageServiceTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final StoredObjectRepository objects = mock(StoredObjectRepository.class);
    private final StorageQuotaService quotas = mock(StorageQuotaService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ZoneOffset.UTC);
    private final StorageProperties properties = new StorageProperties(true, "http://minio:9000", "key",
            "secret", "toolbox-files", 600, 1024, List.of("image/png"));
    private final AdminRequestContext context = new AdminRequestContext("203.0.113.15", "/api/v1/admin/storage/objects", "POST");
    private AdminStorageService service;

    @BeforeEach void setup() {
        service = new AdminStorageService(storage, properties, clock, audit, objects, quotas);
        when(storage.enabled()).thenReturn(true);
        when(storage.bucket()).thenReturn("toolbox-files");
    }

    @Test void uploadValidatesSizeTypeAndAuditsSuccess() throws Exception {
        var tooLarge = new MockMultipartFile("file", "big.png", "image/png", new byte[1025]);
        assertEquals(ApiError.FILE_TOO_LARGE,
                assertThrows(ApiException.class, () -> service.upload(tooLarge, context, 7L)).error());
        var wrongType = new MockMultipartFile("file", "a.zip", "application/zip", new byte[4]);
        assertEquals(ApiError.FILE_TYPE_UNSUPPORTED,
                assertThrows(ApiException.class, () -> service.upload(wrongType, context, 7L)).error());
        var empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertEquals(ApiError.INVALID_ARGUMENT,
                assertThrows(ApiException.class, () -> service.upload(empty, context, 7L)).error());
        var mismatched = new MockMultipartFile("file", "a.pdf", "application/pdf", PNG);
        assertEquals(ApiError.FILE_TYPE_UNSUPPORTED,
                assertThrows(ApiException.class, () -> service.upload(mismatched, context, 7L)).error());
        var binaryText = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{0, 1, 2, 3});
        assertEquals(ApiError.FILE_TYPE_UNSUPPORTED,
                assertThrows(ApiException.class, () -> service.upload(binaryText, context, 7L)).error());
        when(storage.put(anyString(), any(), eq(8L), eq("image/png")))
                .thenReturn(new ObjectStorage.StoredObject("objects/20261005/abcdef.png", 8, "hash", "image/png"));
        var ok = new MockMultipartFile("file", "a.png", "image/png", PNG);
        var response = service.upload(ok, context, 7L);
        assertTrue(response.key().startsWith("objects/20261005/"));
        assertEquals("hash", response.sha256());
        verify(quotas).check("ADMIN", 7L, 8L);
        verify(objects).insert("ADMIN", 7L, "objects/20261005/abcdef.png", 8, "hash", "image/png");
        verify(audit).record(7L, "storage", "UPLOAD", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    @Test void presignValidatesKeysExpiryAndExistence() {
        when(storage.exists("objects/20261005/abcdef.png")).thenReturn(true);
        when(storage.presignedGet("objects/20261005/abcdef.png", 600))
                .thenReturn(new ObjectStorage.PresignedUrl("http://127.0.0.1:9000/signed", 600));
        var response = service.presign("objects/20261005/abcdef.png", null);
        assertEquals("http://127.0.0.1:9000/signed", response.url());
        assertEquals(600, response.expiresInSeconds());
        assertEquals(ApiError.INVALID_ARGUMENT,
                assertThrows(ApiException.class, () -> service.presign("../evil", null)).error());
        assertEquals(ApiError.INVALID_ARGUMENT,
                assertThrows(ApiException.class, () -> service.presign("objects/a.png", 10)).error());
        when(storage.exists("objects/missing.png")).thenReturn(false);
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.presign("objects/missing.png", null)).error());
    }

    @Test void deleteRequiresAnExistingObjectAndAudits() {
        when(objects.findActive("objects/missing.png")).thenReturn(Optional.empty());
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.delete("objects/missing.png", context, 7L)).error());
        when(objects.findActive("objects/20261005/abcdef.png")).thenReturn(Optional.of(
                new StoredObjectRepository.StoredObject(1, "ADMIN", 7, "objects/20261005/abcdef.png", 8, "hash",
                        "image/png")));
        when(objects.softDelete("objects/20261005/abcdef.png")).thenReturn(true);
        service.delete("objects/20261005/abcdef.png", context, 7L);
        verify(storage).delete("objects/20261005/abcdef.png");
        verify(objects).softDelete("objects/20261005/abcdef.png");
        verify(audit).record(7L, "storage", "DELETE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    @Test void disabledStorageFailsClosedButStillReportsStatus() {
        when(storage.enabled()).thenReturn(false);
        when(storage.bucket()).thenReturn("");
        when(quotas.snapshot("ADMIN", 7L)).thenReturn(new StorageQuotaService.Snapshot(true, 0, 0, 0, 100, 3, 1000));
        var status = service.status(7L);
        assertFalse(status.enabled());
        assertTrue(status.quotaEnabled());
        assertEquals(100, status.maxOwnerBytes());
        assertThrows(StorageUnavailableException.class,
                () -> service.presign("objects/a.png", null));
        assertThrows(StorageUnavailableException.class,
                () -> service.delete("objects/a.png", context, 7L));
        assertThrows(StorageUnavailableException.class,
                () -> service.upload(new MockMultipartFile("file", "a.png", "image/png", new byte[4]), context, 7L));
    }
}
