package com.cangshuo.toolbox.admin.model;

import java.util.List;

/** Storage configuration summary; never exposes credentials or the endpoint host. */
public record AdminStorageStatusResponse(boolean enabled, String bucket, long maxObjectBytes,
    int presignedExpirySeconds, List<String> allowedContentTypes, boolean quotaEnabled, long ownerUsageBytes,
    long ownerUsageObjects, long totalUsageBytes, long maxOwnerBytes, long maxOwnerObjects, long maxTotalBytes) {
    public AdminStorageStatusResponse {
        allowedContentTypes = List.copyOf(allowedContentTypes);
    }
}
