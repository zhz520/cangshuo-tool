package com.cangshuo.toolbox.storage;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object storage configuration. Storage is disabled by default so a deployment without MinIO keeps
 * working; enabling it requires endpoint credentials and a bucket name.
 */
@ConfigurationProperties(prefix = "toolbox.storage")
public record StorageProperties(boolean enabled, String endpoint, String accessKey,
                                String secretKey, String bucket, int presignedExpirySeconds, long maxObjectBytes,
                                List<String> allowedContentTypes) {

    public static final int MIN_EXPIRY_SECONDS = 60;
    public static final int MAX_EXPIRY_SECONDS = 604_800;
    public static final long MIN_OBJECT_BYTES = 1L;
    public static final long MAX_OBJECT_BYTES = 104_857_600L;

    public StorageProperties {
        endpoint = stripTrailingSlash(endpoint == null || endpoint.isBlank() ? "http://127.0.0.1:9000" : endpoint);
        bucket = bucket == null || bucket.isBlank() ? "toolbox-files" : bucket.strip();
        allowedContentTypes = allowedContentTypes == null || allowedContentTypes.isEmpty()
                ? List.of("image/png", "image/jpeg", "image/webp", "application/pdf", "text/plain")
                : allowedContentTypes.stream().map(value -> value.strip().toLowerCase(java.util.Locale.ROOT))
                        .filter(value -> !value.isEmpty()).distinct().toList();
        if (presignedExpirySeconds == 0) presignedExpirySeconds = 900;
        if (maxObjectBytes == 0) maxObjectBytes = 26_214_400L;
        if (enabled) {
            if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
                throw new IllegalStateException("MINIO_ACCESS_KEY and MINIO_SECRET_KEY are required when storage is enabled");
            }
            if (!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) {
                throw new IllegalStateException("MINIO_BUCKET must be a valid lowercase bucket name");
            }
        }
        if (presignedExpirySeconds < MIN_EXPIRY_SECONDS || presignedExpirySeconds > MAX_EXPIRY_SECONDS) {
            throw new IllegalStateException("MINIO_PRESIGNED_EXPIRY_SECONDS must be between 60 and 604800");
        }
        if (maxObjectBytes < MIN_OBJECT_BYTES || maxObjectBytes > MAX_OBJECT_BYTES) {
            throw new IllegalStateException("MINIO_MAX_OBJECT_BYTES must be between 1 and 104857600");
        }
        if (allowedContentTypes.isEmpty()) {
            throw new IllegalStateException("MINIO_ALLOWED_CONTENT_TYPES must contain at least one type");
        }
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value.strip();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
