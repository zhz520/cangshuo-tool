package com.cangshuo.toolbox.zepp;

import java.time.Instant;
import java.time.LocalDate;

public record ZeppStepsResponse(String status, int steps, LocalDate date, Instant submittedAt,
                               String accountMasked, boolean boundDevice, boolean wechatVerified) {}
