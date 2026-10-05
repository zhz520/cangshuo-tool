package com.cangshuo.toolbox.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Fixed-window request limits; zero values fall back to the documented defaults. */
@ConfigurationProperties(prefix = "toolbox.rate-limit")
public record RateLimitProperties(boolean enabled, int windowSeconds, int maxAnonymousRequests,
                                  int maxAuthenticatedRequests, int maxAuthAttempts) {
    public static final int DEFAULT_WINDOW_SECONDS = 60;
    public static final int DEFAULT_MAX_ANONYMOUS = 600;
    public static final int DEFAULT_MAX_AUTHENTICATED = 3_000;
    public static final int DEFAULT_MAX_AUTH_ATTEMPTS = 30;

    public RateLimitProperties {
        if (windowSeconds == 0) windowSeconds = DEFAULT_WINDOW_SECONDS;
        if (maxAnonymousRequests == 0) maxAnonymousRequests = DEFAULT_MAX_ANONYMOUS;
        if (maxAuthenticatedRequests == 0) maxAuthenticatedRequests = DEFAULT_MAX_AUTHENTICATED;
        if (maxAuthAttempts == 0) maxAuthAttempts = DEFAULT_MAX_AUTH_ATTEMPTS;
        if (windowSeconds < 1 || windowSeconds > 3600) {
            throw new IllegalStateException("RATE_LIMIT_WINDOW_SECONDS must be between 1 and 3600");
        }
        if (maxAnonymousRequests < 1 || maxAuthenticatedRequests < 1 || maxAuthAttempts < 1
                || maxAnonymousRequests > 1_000_000 || maxAuthenticatedRequests > 1_000_000
                || maxAuthAttempts > 1_000_000) {
            throw new IllegalStateException("Rate limit maxima must be between 1 and 1000000");
        }
        if (maxAuthAttempts > maxAnonymousRequests) {
            throw new IllegalStateException("RATE_LIMIT_MAX_AUTH_ATTEMPTS must not exceed the anonymous limit");
        }
    }
}
