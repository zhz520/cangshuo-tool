package com.cangshuo.toolbox.statistics.service;

import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.statistics.repository.StatisticsRepository;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsServiceTest {
    @Test void usesShanghaiMidnightAndFillsEveryMissingDay() {
        var repository = mock(StatisticsRepository.class);
        var clock = Clock.fixed(Instant.parse("2025-12-31T16:01:00Z"), ZoneOffset.UTC);
        var start = Instant.parse("2025-12-25T16:00:00Z");
        var end = Instant.parse("2026-01-01T16:00:00Z");
        when(repository.datedRegistrations(start, end)).thenReturn(Map.of(LocalDate.of(2025, 12, 31), 2L));
        when(repository.datedOpens(any(), any())).thenReturn(Map.of(LocalDate.of(2026, 1, 1), 4L));
        var snapshot = new StatisticsService(repository, new ApiTrafficMeter(clock), clock).snapshot(7);
        assertEquals(LocalDate.of(2025, 12, 26), snapshot.from());
        assertEquals(LocalDate.of(2026, 1, 1), snapshot.to());
        assertEquals(7, snapshot.daily().size());
        assertEquals(0, snapshot.daily().getFirst().registrations());
        assertEquals(2, snapshot.daily().get(5).registrations());
        assertEquals(4, snapshot.daily().getLast().opens());
        assertEquals("Asia/Shanghai", snapshot.timezone());
        verify(repository).summary(snapshot.from(), snapshot.to(), start, end, clock.instant());
    }
    @Test void ninetyDayRangeCrossesLeapDayWithoutDroppingRows() {
        var repo = mock(StatisticsRepository.class);
        var clock = Clock.fixed(Instant.parse("2024-03-01T00:00:00Z"), ZoneOffset.UTC);
        var snapshot = new StatisticsService(repo, new ApiTrafficMeter(clock), clock).snapshot(90);
        assertEquals(90, snapshot.daily().size());
        assertTrue(snapshot.daily().stream().anyMatch(row -> row.date().equals(LocalDate.of(2024, 2, 29))));
        assertEquals(90, snapshot.daily().stream().map(row -> row.date()).distinct().count());
    }
    @Test void rejectsUnsupportedWindowsBeforeAnyDatabaseWork() {
        var repo = mock(StatisticsRepository.class);
        var clock = Clock.systemUTC();
        var service = new StatisticsService(repo, new ApiTrafficMeter(clock), clock);
        for (int days : new int[]{0, 1, 8, 365, -1, Integer.MAX_VALUE}) assertThrows(ApiException.class, () -> service.snapshot(days));
        verifyNoInteractions(repo);
    }
}
