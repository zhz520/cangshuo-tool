package com.cangshuo.toolbox.admin.model;

import java.time.Instant;

/** Admin projection: includes the reporting user's email for triage context. */
public record AdminFeedbackRow(long id, long userId, String userEmail, String type, String content, String contact,
    String status, String reply, Instant repliedAt, Instant createdAt, Instant updatedAt) { }
