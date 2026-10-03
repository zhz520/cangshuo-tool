package com.cangshuo.toolbox.common.exception;

import org.springframework.http.HttpStatus;

public enum ApiError {
    INTERNAL_ERROR(10000, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_ARGUMENT(10001, "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(10002, "Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(10005, "Access denied", HttpStatus.FORBIDDEN),
    NOT_FOUND(10006, "Resource not found", HttpStatus.NOT_FOUND),
    TOO_MANY_REQUESTS(10007, "Too many requests", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(10008, "Service unavailable", HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ApiError(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public static ApiError forStatus(int status) {
        return switch (status) {
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 429 -> TOO_MANY_REQUESTS;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> status >= 400 && status < 500 ? INVALID_ARGUMENT : INTERNAL_ERROR;
        };
    }
}
