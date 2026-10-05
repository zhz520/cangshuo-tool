package com.cangshuo.toolbox.admin.model;

import java.time.Instant;

public record AdminAuthResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt,
                                AdminProfileResponse admin) {
    @Override public String toString() { return "AdminAuthResponse[redacted]"; }
}
