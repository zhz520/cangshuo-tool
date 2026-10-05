package com.cangshuo.toolbox.storage;

import java.io.InputStream;

/** Small abstraction over object storage so file tools never talk to MinIO directly. */
public interface ObjectStorage {
    boolean enabled();

    String bucket();

    StoredObject put(String key, InputStream data, long size, String contentType);

    /** Returns a presigned GET URL that the caller can hand to a client. */
    PresignedUrl presignedGet(String key, int expirySeconds);

    boolean exists(String key);

    void delete(String key);

    record StoredObject(String key, long size, String sha256, String contentType) { }

    record PresignedUrl(String url, int expiresInSeconds) { }
}
