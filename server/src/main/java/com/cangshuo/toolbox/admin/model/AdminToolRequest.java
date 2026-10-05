package com.cangshuo.toolbox.admin.model;

import jakarta.validation.constraints.Size;
import java.util.List;

/** Optional fields are defaulted by AdminToolInput; unknown or mistyped JSON fields fail deserialization. */
public record AdminToolRequest(@Size(max = 64) String code,
                               @Size(max = 128) String name,
                               @Size(max = 500) String description,
                               @Size(max = 32) String categoryCode,
                               @Size(max = 128) String icon,
                               List<@Size(max = 32) String> keywords,
                               @Size(max = 16) String mode,
                               Boolean requiresLogin,
                               @Size(max = 16) String status,
                               Integer version,
                               Integer sortOrder,
                               Boolean featured,
                               @Size(max = 10000) String configJson) {
    @Override public String toString() { return "AdminToolRequest[redacted]"; }
}
