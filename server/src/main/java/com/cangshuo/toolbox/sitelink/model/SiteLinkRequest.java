package com.cangshuo.toolbox.sitelink.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** User submission; the service normalizes and validates the URL before storing. */
public record SiteLinkRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100) @NotBlank @Size(max = 100) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500) @NotBlank @Size(max = 500) String url,
        @Schema(maxLength = 300) @Size(max = 300) String description) {
}
