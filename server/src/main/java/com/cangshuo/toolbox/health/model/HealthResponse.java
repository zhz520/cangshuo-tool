package com.cangshuo.toolbox.health.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record HealthResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "UP") String status) {
}
