package com.cangshuo.toolbox.common.exception;

import org.springframework.http.HttpStatus;

public enum ApiError {
    INTERNAL_ERROR(10000, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_ARGUMENT(10001, "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(10002, "Authentication required", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(20001, "Email or password is incorrect", HttpStatus.UNAUTHORIZED),
    ACCOUNT_EXISTS(20002, "Account already exists", HttpStatus.CONFLICT),
    ADMIN_INVALID_CREDENTIALS(20004, "Administrator credentials are invalid", HttpStatus.UNAUTHORIZED),
    TOOL_CODE_EXISTS(30004, "Tool code already exists", HttpStatus.CONFLICT),
    CATEGORY_NOT_FOUND(30005, "Category not found", HttpStatus.NOT_FOUND),
    CATEGORY_CODE_EXISTS(30006, "Category code already exists", HttpStatus.CONFLICT),
    CATEGORY_IN_USE(30007, "Category still contains tools", HttpStatus.CONFLICT),
    RECOMMENDATION_CODE_EXISTS(30008, "Recommendation code already exists", HttpStatus.CONFLICT),
    RECOMMENDATION_TOOL_NOT_FOUND(30009, "Referenced tool not found", HttpStatus.NOT_FOUND),
    FILE_TOO_LARGE(40001, "File is too large", HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_TYPE_UNSUPPORTED(40002, "File type is not supported", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    QUOTA_EXCEEDED(40004, "Storage quota exceeded", HttpStatus.PAYLOAD_TOO_LARGE),
    AI_QUOTA_EXCEEDED(40005, "Daily AI attempt quota exceeded", HttpStatus.TOO_MANY_REQUESTS),
    AI_PROVIDER_ERROR(50001, "AI provider unavailable", HttpStatus.BAD_GATEWAY),
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
