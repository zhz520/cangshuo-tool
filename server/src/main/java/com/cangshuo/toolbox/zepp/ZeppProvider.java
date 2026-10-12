package com.cangshuo.toolbox.zepp;

import java.time.Instant;
import java.time.LocalDate;

public interface ZeppProvider {
    Submission submit(String normalizedAccount, String password, int steps);
    record Submission(LocalDate date, Instant submittedAt, boolean boundDevice) {}
}
