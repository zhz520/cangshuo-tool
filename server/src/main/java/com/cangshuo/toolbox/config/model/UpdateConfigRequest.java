package com.cangshuo.toolbox.config.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateConfigRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Size(max = 500) String value) {
}
