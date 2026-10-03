package com.cangshuo.toolbox.common.response;

import com.cangshuo.toolbox.common.exception.ApiError;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApiResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0") int code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "success") String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) T data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[0-9a-f]{32}$") String traceId) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(0, "success", data, traceId);
    }

    public static <T> ApiResponse<T> failure(ApiError error, String traceId) {
        return new ApiResponse<>(error.code(), error.message(), null, traceId);
    }
}
