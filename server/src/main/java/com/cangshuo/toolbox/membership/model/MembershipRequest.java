package com.cangshuo.toolbox.membership.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MembershipRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"FREE", "PRO"})
        @NotBlank @Pattern(regexp = "(?i)FREE|PRO") String plan,
        @Schema(types = {"string", "null"}, description = "ISO-8601 UTC 时间；留空表示长期有效", example = "2027-10-11T00:00:00Z")
        String expiresAt) {
}
