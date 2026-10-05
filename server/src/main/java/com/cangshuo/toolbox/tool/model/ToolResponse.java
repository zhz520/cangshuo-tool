package com.cangshuo.toolbox.tool.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Public catalog metadata only; excludes config JSON, database IDs and executable code. */
public record ToolResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "calculator") String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "计算器") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "日常四则运算") String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "CALC") String categoryCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}, example = "calculator") String icon,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> keywords,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"LOCAL", "SERVER", "HYBRID", "WEB"})
        String mode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean requiresLogin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"ENABLED", "DISABLED", "MAINTENANCE"}) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "1") int version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "10") int sortOrder,
        @JsonProperty("isFeatured") @Schema(requiredMode = Schema.RequiredMode.REQUIRED, name = "isFeatured")
        boolean featured) {

    public ToolResponse {
        keywords = List.copyOf(keywords);
    }
}
