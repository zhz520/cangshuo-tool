package com.cangshuo.toolbox.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MinioObjectStorageTest {
    private final MinioClient client = mock(MinioClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ZoneOffset.UTC);
    private final StorageProperties properties = new StorageProperties(true, "http://minio:9000",
            "key", "secret", "toolbox-files", 900, 1024, List.of("image/png"));
    private final MinioObjectStorage storage = new MinioObjectStorage(client, properties, clock);

    @Test void putStreamsOnceAndReturnsTheServerComputedDigest() throws Exception {
        when(client.bucketExists(any())).thenReturn(true);
        when(client.putObject(any(PutObjectArgs.class))).thenAnswer(invocation -> {
            PutObjectArgs args = invocation.getArgument(0);
            args.stream().transferTo(OutputStream.nullOutputStream());
            assertEquals(4L, args.objectSize());
            assertEquals("image/png", args.contentType());
            return null;
        });
        byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
        var stored = storage.put("objects/20261005/abcdef.png", new ByteArrayInputStream(payload), payload.length,
                "image/png");
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        assertEquals(expected, stored.sha256());
        assertEquals(4L, stored.size());
        assertEquals("image/png", stored.contentType());
    }

    @Test void putRejectsOversizedAndUnsupportedPayloads() {
        assertThrows(StorageValidationException.class, () -> storage.put("objects/a.png",
                new ByteArrayInputStream(new byte[0]), 0, "image/png"));
        assertThrows(StorageValidationException.class, () -> storage.put("objects/a.png",
                new ByteArrayInputStream(new byte[1025]), 1025, "image/png"));
        assertThrows(StorageValidationException.class, () -> storage.put("objects/a.zip",
                new ByteArrayInputStream(new byte[4]), 4, "application/zip"));
        verifyNoInteractions(client);
    }

    @Test void presignedUrlsKeepTheSignedEndpointHostAndClampExpiry() throws Exception {
        when(client.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio:9000/toolbox-files/objects/20261005/abcdef.png?X-Amz-Signature=abc");
        var presigned = storage.presignedGet("objects/20261005/abcdef.png", 600);
        assertEquals("http://minio:9000/toolbox-files/objects/20261005/abcdef.png?X-Amz-Signature=abc",
                presigned.url());
        assertEquals(600, presigned.expiresInSeconds());
        var clamped = storage.presignedGet("objects/20261005/abcdef.png", 5);
        assertEquals(60, clamped.expiresInSeconds());
    }

    @Test void keyValidationNormalizesAndRejectsTraversal() {
        assertEquals("objects/20261005/abcdef.png", StorageKeys.requireValid(" Objects/20261005/ABCDEF.png "));
        for (String bad : List.of("../etc/passwd", "objects//abcdef.png", "objects/abcdef.", "objects/", "")) {
            assertThrows(IllegalArgumentException.class, () -> StorageKeys.requireValid(bad));
        }
    }
}
