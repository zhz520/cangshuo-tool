package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminPresignedResponse;
import com.cangshuo.toolbox.admin.model.AdminStorageStatusResponse;
import com.cangshuo.toolbox.admin.model.AdminStoredObjectResponse;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.storage.ObjectStorage;
import com.cangshuo.toolbox.storage.FileSignatures;
import com.cangshuo.toolbox.storage.StorageKeys;
import com.cangshuo.toolbox.storage.StorageProperties;
import com.cangshuo.toolbox.storage.StorageUnavailableException;
import com.cangshuo.toolbox.storage.StorageQuotaService;
import com.cangshuo.toolbox.storage.StoredObjectRepository;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Administrator storage operations: bounded upload, presigned download and delete. */
@Service
public class AdminStorageService {
    private static final String MODULE = "storage";
    private final ObjectStorage storage;
    private final StorageProperties properties;
    private final Clock clock;
    private final AdminAuditService audit;
    private final StoredObjectRepository objects;
    private final StorageQuotaService quotas;

    public AdminStorageService(ObjectStorage storage, StorageProperties properties, Clock clock,
                               AdminAuditService audit, StoredObjectRepository objects, StorageQuotaService quotas) {
        this.storage = storage; this.properties = properties; this.clock = clock; this.audit = audit;
        this.objects = objects; this.quotas = quotas;
    }

    public AdminStorageStatusResponse status(Long adminId) {
        var quota = quotas.snapshot("ADMIN", ownerId(adminId));
        return new AdminStorageStatusResponse(storage.enabled(), storage.bucket(), properties.maxObjectBytes(),
                properties.presignedExpirySeconds(), properties.allowedContentTypes(), quota.enabled(),
                quota.ownerBytes(), quota.ownerObjects(), quota.totalBytes(), quota.maxOwnerBytes(),
                quota.maxOwnerObjects(), quota.maxTotalBytes());
    }

    public AdminStoredObjectResponse upload(MultipartFile file, AdminRequestContext context, Long adminId) {
        requireEnabled();
        if (file == null || file.isEmpty()) throw new ApiException(ApiError.INVALID_ARGUMENT);
        if (file.getSize() > properties.maxObjectBytes()) throw new ApiException(ApiError.FILE_TOO_LARGE);
        String contentType = StorageKeys.normalizeContentType(file.getContentType());
        if (!properties.allowedContentTypes().contains(contentType)) {
            throw new ApiException(ApiError.FILE_TYPE_UNSUPPORTED);
        }
        long ownerId = ownerId(adminId);
        quotas.check("ADMIN", ownerId, file.getSize());
        String key = StorageKeys.generate(clock, contentType);
        try (InputStream raw = file.getInputStream();
             BufferedInputStream data = new BufferedInputStream(raw)) {
            data.mark(FileSignatures.HEADER_BYTES);
            byte[] header = data.readNBytes(FileSignatures.HEADER_BYTES);
            data.reset();
            if (!FileSignatures.matches(contentType, FileSignatures.detect(header))) {
                throw new ApiException(ApiError.FILE_TYPE_UNSUPPORTED);
            }
            var stored = storage.put(key, data, file.getSize(), contentType);
            objects.insert("ADMIN", ownerId, stored.key(), stored.size(), stored.sha256(), stored.contentType());
            audit.record(adminId, MODULE, "UPLOAD", context.uri(), context.method(), context.ip(), "SUCCESS");
            return new AdminStoredObjectResponse(stored.key(), stored.size(), stored.sha256(), stored.contentType());
        } catch (IOException exception) {
            throw new StorageUnavailableException("Upload stream failed", exception);
        }
    }

    public AdminPresignedResponse presign(String rawKey, Integer expirySeconds) {
        requireEnabled();
        String key = key(rawKey);
        int expiry;
        if (expirySeconds == null) {
            expiry = properties.presignedExpirySeconds();
        } else if (expirySeconds < StorageProperties.MIN_EXPIRY_SECONDS
                || expirySeconds > StorageProperties.MAX_EXPIRY_SECONDS) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        } else {
            expiry = expirySeconds;
        }
        if (!storage.exists(key)) throw new ApiException(ApiError.NOT_FOUND);
        var presigned = storage.presignedGet(key, expiry);
        return new AdminPresignedResponse(presigned.url(), presigned.expiresInSeconds());
    }

    public void delete(String rawKey, AdminRequestContext context, Long adminId) {
        requireEnabled();
        String key = key(rawKey);
        if (objects.findActive(key).isEmpty()) throw new ApiException(ApiError.NOT_FOUND);
        storage.delete(key);
        if (!objects.softDelete(key)) throw new StorageUnavailableException("Stored object metadata update failed");
        audit.record(adminId, MODULE, "DELETE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    private static long ownerId(Long adminId) {
        if (adminId == null) throw new ApiException(ApiError.UNAUTHENTICATED);
        return adminId;
    }

    private void requireEnabled() {
        if (!storage.enabled()) throw new StorageUnavailableException("Object storage is disabled");
    }

    private static String key(String rawKey) {
        try {
            return StorageKeys.requireValid(rawKey);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
    }
}
