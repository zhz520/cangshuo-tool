package com.cangshuo.toolbox.admin.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AdminSiteLinkUpdateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"APPROVED", "REJECTED", "PENDING"})
        @NotBlank String status) {
}
