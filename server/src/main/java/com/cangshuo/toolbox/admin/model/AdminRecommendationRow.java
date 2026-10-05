package com.cangshuo.toolbox.admin.model;

import java.time.Instant;

/** A home recommendation slot; exactly one of toolCode/linkUrl is set for active rows. */
public record AdminRecommendationRow(long id, String slotCode, String title, String subtitle, String toolCode,
    String linkUrl, String imageUrl, int sortOrder, boolean enabled, Instant startAt, Instant endAt,
    boolean active, Instant updatedAt) { }
