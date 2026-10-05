package com.cangshuo.toolbox.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Storage quota limits per owner and in total; zero means "use the built-in default". */
@ConfigurationProperties(prefix = "toolbox.quota")
public record QuotaProperties(boolean enabled, long maxOwnerBytes, long maxOwnerObjects, long maxTotalBytes) {
    public static final long DEFAULT_MAX_OWNER_BYTES = 104_857_600L;
    public static final long DEFAULT_MAX_OWNER_OBJECTS = 200L;
    public static final long DEFAULT_MAX_TOTAL_BYTES = 1_073_741_824L;
    public static final long MAX_ALLOWED_TOTAL_BYTES = 10_995_116_277_760L;

    public QuotaProperties {
        if (maxOwnerBytes == 0) maxOwnerBytes = DEFAULT_MAX_OWNER_BYTES;
        if (maxOwnerObjects == 0) maxOwnerObjects = DEFAULT_MAX_OWNER_OBJECTS;
        if (maxTotalBytes == 0) maxTotalBytes = DEFAULT_MAX_TOTAL_BYTES;
        if (maxOwnerBytes < 1 || maxOwnerObjects < 1 || maxTotalBytes < 1
                || maxOwnerBytes > MAX_ALLOWED_TOTAL_BYTES || maxTotalBytes > MAX_ALLOWED_TOTAL_BYTES) {
            throw new IllegalStateException("Storage quota limits must be positive and within 10 TiB");
        }
        if (maxOwnerBytes > maxTotalBytes) {
            throw new IllegalStateException("STORAGE_MAX_OWNER_BYTES must not exceed STORAGE_MAX_TOTAL_BYTES");
        }
    }
}
