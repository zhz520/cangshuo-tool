package com.cangshuo.toolbox.admin.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory administrator login throttle: five failures lock the account or address for fifteen minutes. */
@Component
public class AdminLoginGuard {
    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final int PRUNE_THRESHOLD = 512;
    private final Clock clock;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public AdminLoginGuard(Clock clock) { this.clock = clock; }

    public boolean locked(String key) {
        State state = states.get(key);
        if (state == null || state.lockedUntil == null) return false;
        if (clock.instant().isBefore(state.lockedUntil)) return true;
        states.remove(key);
        return false;
    }

    public void recordFailure(String key) {
        states.compute(key, (ignored, state) -> {
            Instant now = clock.instant();
            int previous = state == null || (state.lockedUntil != null && !now.isBefore(state.lockedUntil))
                    ? 0 : state.failures;
            int failures = previous + 1;
            return new State(failures, failures >= MAX_FAILURES ? now.plus(LOCK_DURATION) : null, now);
        });
        if (states.size() > PRUNE_THRESHOLD) {
            Instant cutoff = clock.instant().minus(LOCK_DURATION);
            states.values().removeIf(state -> state.touchedAt.isBefore(cutoff));
        }
    }

    public void clear(String key) { states.remove(key); }

    private record State(int failures, Instant lockedUntil, Instant touchedAt) { }
}
