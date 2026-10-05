package com.cangshuo.toolbox.admin.model;

import jakarta.validation.constraints.Size;

public record AdminCategoryRequest(@Size(max = 32) String code,
                                   @Size(max = 64) String name,
                                   @Size(max = 500) String description,
                                   @Size(max = 128) String icon,
                                   Integer sortOrder,
                                   Boolean enabled) {
    @Override public String toString() { return "AdminCategoryRequest[redacted]"; }
}
