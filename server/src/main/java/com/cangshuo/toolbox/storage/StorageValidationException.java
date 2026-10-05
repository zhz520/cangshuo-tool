package com.cangshuo.toolbox.storage;

/** Raised for client-side storage input problems; mapped to 400/10001. */
public class StorageValidationException extends RuntimeException {
    public StorageValidationException(String message) { super(message); }
}
