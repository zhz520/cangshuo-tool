package com.cangshuo.toolbox.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** MinIO implementation; the bucket is created lazily on first use. */
public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final StorageProperties properties;
    private final Clock clock;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    public MinioObjectStorage(MinioClient client, StorageProperties properties, Clock clock) {
        this.client = client; this.properties = properties; this.clock = clock;
    }

    @Override public boolean enabled() { return true; }

    @Override public String bucket() { return properties.bucket(); }

    @Override public StoredObject put(String key, InputStream data, long size, String contentType) {
        if (size < StorageProperties.MIN_OBJECT_BYTES || size > properties.maxObjectBytes()) {
            throw new StorageValidationException("Object size is outside the allowed range");
        }
        String normalizedType = StorageKeys.normalizeContentType(contentType);
        if (!properties.allowedContentTypes().contains(normalizedType)) {
            throw new StorageValidationException("Content type is not allowed");
        }
        MessageDigest digest = sha256();
        try (DigestInputStream stream = new DigestInputStream(data, digest)) {
            ensureBucket();
            client.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(key)
                    .stream(stream, size, -1).contentType(normalizedType).build());
        } catch (Exception exception) {
            throw new StorageUnavailableException("Upload to object storage failed", exception);
        }
        return new StoredObject(key, size, HexFormat.of().formatHex(digest.digest()), normalizedType);
    }

    @Override public PresignedUrl presignedGet(String key, int expirySeconds) {
        int expiry = Math.min(Math.max(expirySeconds, StorageProperties.MIN_EXPIRY_SECONDS),
                StorageProperties.MAX_EXPIRY_SECONDS);
        try {
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                    .bucket(properties.bucket()).object(key).expiry(expiry, TimeUnit.SECONDS).build());
            // Presigned URLs are signed for the configured endpoint host; clients must resolve that host.
            return new PresignedUrl(url, expiry);
        } catch (Exception exception) {
            throw new StorageUnavailableException("Presigned URL generation failed", exception);
        }
    }

    @Override public boolean exists(String key) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(properties.bucket()).object(key).build());
            return true;
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse() == null ? "" : exception.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code)) return false;
            throw new StorageUnavailableException("Object stat failed", exception);
        } catch (Exception exception) {
            throw new StorageUnavailableException("Object stat failed", exception);
        }
    }

    @Override public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(key).build());
        } catch (Exception exception) {
            throw new StorageUnavailableException("Object delete failed", exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady.get()) return;
        synchronized (bucketReady) {
            if (bucketReady.get()) return;
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
                try {
                    client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                } catch (ErrorResponseException exception) {
                    String code = exception.errorResponse() == null ? "" : exception.errorResponse().code();
                    if (!"BucketAlreadyOwnedByYou".equals(code) && !"BucketAlreadyExists".equals(code)) throw exception;
                }
            }
            bucketReady.set(true);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
