package com.cangshuo.toolbox.common.exception;

/** Stable public failure without embedding request values or credentials. */
public final class ApiException extends RuntimeException {
    private final ApiError error;

    public ApiException(ApiError error) {
        super(error.name());
        this.error = error;
    }

    public ApiError error() { return error; }
}
