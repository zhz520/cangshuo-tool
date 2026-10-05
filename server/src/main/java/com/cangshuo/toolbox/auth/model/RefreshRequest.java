package com.cangshuo.toolbox.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RefreshRequest(@NotBlank @Pattern(regexp = "^[0-9a-f]{32}\\.[A-Za-z0-9_-]{43}$") String refreshToken) {
    @Override public String toString() { return "RefreshRequest[redacted]"; }
}
