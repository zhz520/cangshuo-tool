package com.cangshuo.toolbox.storage;

import java.io.InputStream;

/** Active when toolbox.storage.enabled is false; every operation fails with 503/10008. */
public class DisabledObjectStorage implements ObjectStorage {
    @Override public boolean enabled() { return false; }

    @Override public String bucket() { return ""; }

    @Override public StoredObject put(String key, InputStream data, long size, String contentType) {
        throw new StorageUnavailableException("Object storage is disabled");
    }

    @Override public PresignedUrl presignedGet(String key, int expirySeconds) {
        throw new StorageUnavailableException("Object storage is disabled");
    }

    @Override public boolean exists(String key) { return false; }

    @Override public void delete(String key) { throw new StorageUnavailableException("Object storage is disabled"); }
}
