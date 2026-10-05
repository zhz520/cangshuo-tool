package com.cangshuo.toolbox.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiRequest(@NotBlank @Size(max = 16) String task, @NotBlank @Size(max = 8000) String text,
    @Size(max = 16) String targetLanguage) {
    @Override public String toString() { return "AiRequest[redacted]"; }
}
