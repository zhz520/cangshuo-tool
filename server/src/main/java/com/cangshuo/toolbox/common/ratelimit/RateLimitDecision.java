package com.cangshuo.toolbox.common.ratelimit;

public record RateLimitDecision(boolean allowed, int limit, int remaining, int retryAfterSeconds) { }
