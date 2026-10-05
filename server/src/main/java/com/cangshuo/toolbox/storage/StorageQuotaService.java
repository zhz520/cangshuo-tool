package com.cangshuo.toolbox.storage;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import org.springframework.stereotype.Service;

/** Enforces per-owner and global object-storage quotas before any bytes are stored. */
@Service
public class StorageQuotaService {
    private final StoredObjectRepository objects;
    private final QuotaProperties properties;

    public StorageQuotaService(StoredObjectRepository objects, QuotaProperties properties) {
        this.objects = objects; this.properties = properties;
    }

    public record Snapshot(boolean enabled, long ownerBytes, long ownerObjects, long totalBytes,
        long maxOwnerBytes, long maxOwnerObjects, long maxTotalBytes) { }

    public void check(String ownerType, long ownerId, long incomingBytes) {
        if (!properties.enabled()) return;
        var usage = objects.usage(ownerType, ownerId);
        if (usage.objects() + 1 > properties.maxOwnerObjects()
                || usage.bytes() + incomingBytes > properties.maxOwnerBytes()
                || objects.totalBytes() + incomingBytes > properties.maxTotalBytes()) {
            throw new ApiException(ApiError.QUOTA_EXCEEDED);
        }
    }

    public Snapshot snapshot(String ownerType, long ownerId) {
        var usage = objects.usage(ownerType, ownerId);
        return new Snapshot(properties.enabled(), usage.bytes(), usage.objects(), objects.totalBytes(),
                properties.maxOwnerBytes(), properties.maxOwnerObjects(), properties.maxTotalBytes());
    }
}
