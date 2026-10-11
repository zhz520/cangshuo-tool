package com.cangshuo.toolbox.statistics.service;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.statistics.model.StatisticsResponse;
import com.cangshuo.toolbox.statistics.repository.StatisticsRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final StatisticsRepository repository;
    private final ApiTrafficMeter traffic;
    private final Clock clock;
    public StatisticsService(StatisticsRepository repository, ApiTrafficMeter traffic, Clock clock) {
        this.repository = repository; this.traffic = traffic; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public StatisticsResponse snapshot(int days) {
        if (days != 7 && days != 30 && days != 90) throw new ApiException(ApiError.INVALID_ARGUMENT);
        var now = clock.instant();
        var to = now.atZone(ZONE).toLocalDate();
        var from = to.minusDays(days - 1L);
        var start = from.atStartOfDay(ZONE).toInstant();
        var end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        var registrations = repository.datedRegistrations(start, end);
        var opens = repository.datedOpens(from, to);
        var activity = repository.datedActivity(from, to);
        var feedback = repository.datedFeedback(start, end);
        var submissions = repository.datedSubmissions(start, end);
        var daily = from.datesUntil(to.plusDays(1)).map(day -> new StatisticsResponse.Daily(day,
                registrations.getOrDefault(day, 0L), opens.getOrDefault(day, 0L),
                activity.getOrDefault(day, 0L), feedback.getOrDefault(day, 0L), submissions.getOrDefault(day, 0L))).toList();
        return new StatisticsResponse(days, from, to, ZONE.getId(), now, repository.collectionSince(),
                repository.summary(from, to, start, end, now), daily,
                repository.ranking(from, to), repository.categories(), repository.modes(), traffic.snapshot());
    }
}
