package com.cangshuo.toolbox.statistics.web;

import com.cangshuo.toolbox.statistics.repository.StatisticsRepository;
import com.cangshuo.toolbox.statistics.service.ApiTrafficMeter;
import jakarta.servlet.ServletException;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsTelemetryTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T16:01:00Z"), ZoneOffset.UTC);
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }
    private void authenticate(boolean admin) {
        var token = Jwt.withTokenValue("test").header("alg", "HS256").subject("7");
        if (admin) token.claim("kind", "admin");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token.build(), List.of()));
    }
    @Test void successfulUserRequestsTrackShanghaiDayButExcludeFailuresAdminsAndDeletion() {
        var repo = mock(StatisticsRepository.class);
        var interceptor = new UserActivityInterceptor(repo, new ApiTrafficMeter(clock), clock);
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        var response = new MockHttpServletResponse();
        interceptor.afterCompletion(request, response, new Object(), null); // anonymous
        authenticate(true);
        interceptor.afterCompletion(request, response, new Object(), null);
        authenticate(false);
        response.setStatus(401);
        interceptor.afterCompletion(request, response, new Object(), null);
        response.setStatus(200);
        interceptor.afterCompletion(request, response, new Object(), new RuntimeException());
        interceptor.afterCompletion(new MockHttpServletRequest("DELETE", "/api/v1/auth/me"), response, new Object(), null);
        verifyNoInteractions(repo);
        interceptor.afterCompletion(request, response, new Object(), null);
        verify(repo).recordActivity(7, LocalDate.of(2026, 10, 12));
    }
    @Test void activityStorageFailurePreservesBusinessResponseAndReportsMissingTelemetry() {
        var repo = mock(StatisticsRepository.class);
        doThrow(new IllegalStateException()).when(repo).recordActivity(anyLong(), any());
        var meter = new ApiTrafficMeter(clock);
        authenticate(false);
        var response = new MockHttpServletResponse();
        new UserActivityInterceptor(repo, meter, clock).afterCompletion(new MockHttpServletRequest("GET", "/api/v1/auth/me"), response, new Object(), null);
        assertEquals(200, response.getStatus());
        assertEquals(1, meter.snapshot().activityWriteFailures());
    }
    @Test void filterCountsSecurityRejectionsAndUnhandledErrorsAndSkipsProbes() throws Exception {
        var meter = new ApiTrafficMeter(clock);
        var filter = new ApiTrafficFilter(meter);
        for (int status : new int[]{200, 401, 403, 500}) {
            filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/admin/statistics"), new MockHttpServletResponse(),
                (request, response) -> ((MockHttpServletResponse) response).setStatus(status));
        }
        assertThrows(ServletException.class, () -> filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/broken"),
            new MockHttpServletResponse(), (req, res) -> { throw new ServletException(); }));
        for (var request : List.of(new MockHttpServletRequest("GET", "/api/v1/health"),
            new MockHttpServletRequest("OPTIONS", "/api/v1/tools"), new MockHttpServletRequest("GET", "/actuator/health"))) {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        }
        var result = meter.snapshot();
        assertEquals(5, result.requests()); assertEquals(2, result.clientErrors()); assertEquals(2, result.serverErrors());
        assertTrue(result.averageMs() >= 0); assertEquals(clock.instant(), result.since());
    }
}
