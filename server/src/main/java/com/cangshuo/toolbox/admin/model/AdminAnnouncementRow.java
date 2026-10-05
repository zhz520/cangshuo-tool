package com.cangshuo.toolbox.admin.model;

import java.time.Instant;

public record AdminAnnouncementRow(long id, String title, String body, String level, boolean published,
    Instant startAt, Instant endAt, boolean active, Instant createdAt, Instant updatedAt) { }
