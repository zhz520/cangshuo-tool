package com.cangshuo.toolbox.storage;

/** Raised when object storage is disabled or an operation fails; mapped to 503/10008. */
public class StorageUnavailableException extends RuntimeException {
    public StorageUnavailableException(String message) { super(message); }
    public StorageUnavailableException(String message, Throwable cause) { super(message, cause); }
}
