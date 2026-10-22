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
    ZEPP_INVALID_CREDENTIALS(60001, "Zepp Life credentials rejected", HttpStatus.UNPROCESSABLE_ENTITY),
    ZEPP_VERIFICATION_REQUIRED(60002, "Complete verification in Zepp Life", HttpStatus.UNPROCESSABLE_ENTITY),
    ZEPP_PROVIDER_ERROR(60003, "Zepp Life request failed", HttpStatus.BAD_GATEWAY),
    ZEPP_RATE_LIMITED(60004, "Zepp Life rate limit reached", HttpStatus.TOO_MANY_REQUESTS),
    ZEPP_SUBMISSION_UNCERTAIN(60005, "Submission result is unknown; check Zepp Life before retrying", HttpStatus.GATEWAY_TIMEOUT),
    ZEPP_NOT_CONFIGURED(60006, "Zepp Life service is not configured", HttpStatus.SERVICE_UNAVAILABLE),
    ZEPP_ACCOUNT_COOLDOWN(60007, "Wait before submitting this account again", HttpStatus.TOO_MANY_REQUESTS),
    MEDIA_UNSUPPORTED_PLATFORM(61001, "Unsupported sharing platform", HttpStatus.BAD_REQUEST),
    MEDIA_UNAVAILABLE(61002, "Public media unavailable or verification required", HttpStatus.UNPROCESSABLE_ENTITY),
    MEDIA_PROVIDER_ERROR(61003, "Media provider request failed", HttpStatus.BAD_GATEWAY),
    MEDIA_RESULT_EXPIRED(61004, "Parsed result expired; parse the link again", HttpStatus.GONE),
    MEDIA_TOO_LARGE(61005, "Media exceeds the processing budget", HttpStatus.PAYLOAD_TOO_LARGE),
    MEDIA_TIMEOUT(61006, "Media provider timed out", HttpStatus.GATEWAY_TIMEOUT),
    MEDIA_FORMAT_UNSUPPORTED(61007, "Media format not supported", HttpStatus.UNPROCESSABLE_ENTITY),
    MEDIA_LINK_EXPIRED(61008, "Share link expired or note unavailable; copy a fresh link", HttpStatus.GONE),
    MEDIA_PLATFORM_RESTRICTED(61009, "Platform restricted anonymous access; try later or configure operator access", HttpStatus.SERVICE_UNAVAILABLE),
    LANZOU_LINK_INVALID(62001, "The Lanzou share link is invalid", HttpStatus.BAD_REQUEST),
    LANZOU_PASSWORD_REQUIRED(62002, "A share password is required", HttpStatus.UNPROCESSABLE_ENTITY),
    LANZOU_PASSWORD_INVALID(62003, "The share password was rejected", HttpStatus.UNPROCESSABLE_ENTITY),
    LANZOU_FILE_UNAVAILABLE(62004, "The shared file is unavailable", HttpStatus.GONE),
    LANZOU_VERIFICATION_REQUIRED(62005, "Complete verification on the official Lanzou page", HttpStatus.UNPROCESSABLE_ENTITY),
    LANZOU_RESTRICTED(62006, "Lanzou temporarily restricted this server; wait before retrying", HttpStatus.SERVICE_UNAVAILABLE),
    LANZOU_PROVIDER_ERROR(62007, "Lanzou returned an unexpected response", HttpStatus.BAD_GATEWAY),
    LANZOU_TIMEOUT(62008, "Lanzou request timed out", HttpStatus.GATEWAY_TIMEOUT),
    LANZOU_FOLDER_UNSUPPORTED(62009, "Folder shares are not supported", HttpStatus.UNPROCESSABLE_ENTITY),
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
