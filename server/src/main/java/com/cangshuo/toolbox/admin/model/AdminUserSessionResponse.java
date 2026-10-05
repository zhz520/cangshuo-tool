package com.cangshuo.toolbox.admin.model;

import java.time.Instant;

/** Refresh sessions are masked and never expose token hashes or the full selector. */
public record AdminUserSessionResponse(long sessionId, String sessionCodeMasked, Instant createdAt,
    Instant expiresAt, Instant revokedAt, Instant lastActiveAt) { }
