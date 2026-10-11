package com.cangshuo.toolbox.app;

import io.swagger.v3.oas.annotations.media.Schema;

/** Latest client release metadata; values come from server configuration. */
public record AppVersionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "1") long versionCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0.1.0") String versionName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String url,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String notes) {
}
