package com.cangshuo.toolbox.statistics.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record StatisticsResponse(int days, LocalDate from, LocalDate to, String timezone,
        Instant generatedAt, Instant collectionSince, Map<String, Long> summary,
        List<Daily> daily, List<Rank> ranking, List<Distribution> categories,
        List<Distribution> modes, Traffic traffic) {
    public record Daily(LocalDate date, long registrations, long opens, long activeUsers,
            long feedback, long submissions) {}
    public record Rank(String code, String name, String category, String status,
            long opens, long allTimeOpens) {}
    public record Distribution(String code, String name, long count, long opens) {}
    public record Traffic(Instant since, long requests, long clientErrors, long serverErrors,
            double averageMs, long activityWriteFailures) {}
}
