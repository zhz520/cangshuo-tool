package com.cangshuo.toolbox.sitelink.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Public navigation entry; never exposes the submitting account. */
public record SiteLinkResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12") long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "GitHub") String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "https://github.com") String url,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-11T13:30:00Z") String createdAt) {
}
