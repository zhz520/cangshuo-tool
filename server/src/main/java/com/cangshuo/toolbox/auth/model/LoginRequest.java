package com.cangshuo.toolbox.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 128) String email,
                           @NotBlank @Size(min = 8, max = 72) String password) {
    @Override public String toString() { return "LoginRequest[redacted]"; }
}
