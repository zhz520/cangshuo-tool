package com.cangshuo.toolbox.admin.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(@NotBlank @Size(min = 3, max = 32) String username,
                                @NotBlank @Size(min = 8, max = 72) String password) {
    @Override public String toString() { return "AdminLoginRequest[redacted]"; }
}
