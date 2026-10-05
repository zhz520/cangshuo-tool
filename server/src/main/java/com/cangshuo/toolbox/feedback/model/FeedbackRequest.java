package com.cangshuo.toolbox.feedback.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(@NotBlank @Size(max = 16) String type,
                              @NotBlank @Size(max = 2000) String content,
                              @Size(max = 128) String contact) {
    @Override public String toString() { return "FeedbackRequest[redacted]"; }
}
