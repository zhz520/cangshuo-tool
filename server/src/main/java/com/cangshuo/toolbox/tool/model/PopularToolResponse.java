package com.cangshuo.toolbox.tool.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Public popularity entry: catalog metadata plus the aggregated anonymous use count. */
public record PopularToolResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "calculator") String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "计算器") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}) String icon,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "128")
        long useCount) {
}
