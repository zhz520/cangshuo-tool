package com.cangshuo.toolbox.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank @Size(max = 128) String email,
                              @NotBlank @Size(min = 8, max = 72) String password,
                              @NotBlank @Size(max = 64) String nickname) {
    @Override public String toString() { return "RegisterRequest[redacted]"; }
}
