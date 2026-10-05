package com.cangshuo.toolbox.admin.model;

import java.time.Instant;

public record AdminUserRow(long id, String email, String nickname, boolean enabled, Instant lastLoginAt,
    Instant createdAt, Instant updatedAt, long favoriteCount, long recentCount) { }
