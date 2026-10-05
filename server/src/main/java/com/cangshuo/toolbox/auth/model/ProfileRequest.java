package com.cangshuo.toolbox.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileRequest(@NotBlank @Size(max = 64) String nickname) {
    @Override public String toString() { return "ProfileRequest[redacted]"; }
}
