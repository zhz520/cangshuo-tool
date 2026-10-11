package com.cangshuo.toolbox.membership.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Current membership of the signed-in account; grants are issued by administrators. */
public record MembershipResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"FREE", "PRO"}) String plan,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}) String expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {
}
