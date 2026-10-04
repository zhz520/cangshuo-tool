package com.cangshuo.toolbox.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** One-based page of catalog data, wrapped by ApiResponse at the HTTP boundary. */
public record PageResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> records,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "1") int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "100", example = "20")
        int pageSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", example = "0") long total) {

    public PageResponse {
        records = List.copyOf(records);
    }
}
