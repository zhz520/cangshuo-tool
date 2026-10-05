package com.cangshuo.toolbox.auth.model;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, long expiresIn,
                           Instant expiresAt, UserResponse user, String refreshToken, Instant refreshExpiresAt) {
    @Override public String toString() { return "AuthResponse[redacted]"; }
}
