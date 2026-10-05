package com.cangshuo.toolbox.admin.model;

import jakarta.validation.constraints.Size;

public record AdminAnnouncementRequest(@Size(max = 128) String title,
                                       @Size(max = 2000) String body,
                                       @Size(max = 16) String level,
                                       Boolean enabled,
                                       @Size(max = 40) String startAt,
                                       @Size(max = 40) String endAt) {
    @Override public String toString() { return "AdminAnnouncementRequest[redacted]"; }
}
