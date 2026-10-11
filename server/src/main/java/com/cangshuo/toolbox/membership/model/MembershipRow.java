package com.cangshuo.toolbox.membership.model;

import java.time.Instant;

public record MembershipRow(String plan, Instant expiresAt) {
}
