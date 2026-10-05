package com.cangshuo.toolbox.admin.model;

import jakarta.validation.constraints.Size;

public record AdminRecommendationRequest(@Size(max = 32) String slotCode,
                                         @Size(max = 64) String title,
                                         @Size(max = 128) String subtitle,
                                         @Size(max = 64) String toolCode,
                                         @Size(max = 500) String linkUrl,
                                         @Size(max = 500) String imageUrl,
                                         Integer sortOrder,
                                         Boolean enabled,
                                         @Size(max = 40) String startAt,
                                         @Size(max = 40) String endAt) {
    @Override public String toString() { return "AdminRecommendationRequest[redacted]"; }
}
