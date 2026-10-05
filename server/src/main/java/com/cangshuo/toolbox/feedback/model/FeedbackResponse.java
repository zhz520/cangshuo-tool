package com.cangshuo.toolbox.feedback.model;

import java.time.Instant;

/** User-facing feedback view; never exposes other users or internal audit fields. */
public record FeedbackResponse(long id, String type, String content, String contact, String status, String reply,
    Instant repliedAt, Instant createdAt, Instant updatedAt) { }
