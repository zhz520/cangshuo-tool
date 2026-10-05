package com.cangshuo.toolbox.admin.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminFeedbackUpdateRequest(@NotBlank @Size(max = 16) String status,
                                         @Size(max = 2000) String reply) {
    @Override public String toString() { return "AdminFeedbackUpdateRequest[redacted]"; }
}
