package com.cangshuo.toolbox.statistics.service;

import com.cangshuo.toolbox.statistics.model.StatisticsResponse.Traffic;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/** Bounded, aggregate-only process telemetry: no URLs, identifiers or payloads. */
@Component
public class ApiTrafficMeter {
    private final Instant since;
    private final LongAdder requests = new LongAdder();
    private final LongAdder clientErrors = new LongAdder();
    private final LongAdder serverErrors = new LongAdder();
    private final LongAdder nanos = new LongAdder();
    private final LongAdder writeFailures = new LongAdder();
    public ApiTrafficMeter(Clock clock) { since = clock.instant(); }
    public void completed(int status, long elapsedNanos) {
        nanos.add(Math.max(0, elapsedNanos));
        if (status >= 500) serverErrors.increment();
        else if (status >= 400) clientErrors.increment();
        requests.increment();
    }
    public void activityWriteFailed() { writeFailures.increment(); }
    public Traffic snapshot() {
        long count = requests.sum();
        double average = count == 0 ? 0 : nanos.sum() / 1_000_000.0 / count;
        return new Traffic(since, count, clientErrors.sum(), serverErrors.sum(),
                Math.round(average * 100.0) / 100.0, writeFailures.sum());
    }
}
