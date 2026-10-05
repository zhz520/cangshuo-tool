package com.cangshuo.toolbox.auth.model;

import java.time.Instant;

public record RefreshSession(long id, String code, long userId, Instant expiresAt, Instant revokedAt) {
    @Override public String toString() { return "RefreshSession[redacted]"; }
}
