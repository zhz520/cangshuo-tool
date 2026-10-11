package com.cangshuo.toolbox.config.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record ConfigEntry(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "APP_LATEST_VERSION_NAME") String key,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String value) {
}
