package com.cangshuo.toolbox.statistics.web;

import com.cangshuo.toolbox.statistics.repository.StatisticsRepository;
import com.cangshuo.toolbox.statistics.service.ApiTrafficMeter;
import com.cangshuo.toolbox.statistics.service.StatisticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

public class UserActivityInterceptor implements HandlerInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(UserActivityInterceptor.class);
    private final StatisticsRepository repository;
    private final ApiTrafficMeter traffic;
    private final Clock clock;
    public UserActivityInterceptor(StatisticsRepository repository, ApiTrafficMeter traffic, Clock clock) {
        this.repository = repository; this.traffic = traffic; this.clock = clock;
    }
    @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        if (exception != null || response.getStatus() >= 400 || "OPTIONS".equals(request.getMethod())
                || ("DELETE".equals(request.getMethod()) && request.getRequestURI().endsWith("/auth/me"))) return;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()
                || "admin".equals(jwt.getToken().getClaimAsString("kind"))) return;
        String subject = jwt.getToken().getSubject();
        if (subject == null || !subject.matches("[1-9][0-9]{0,18}")) return;
        try { repository.recordActivity(Long.parseLong(subject), LocalDate.now(clock.withZone(StatisticsService.ZONE))); }
        catch (RuntimeException failure) {
            // A deletion race or telemetry failure must never change the business response.
            traffic.activityWriteFailed();
            LOG.warn("Daily activity write failed ({})", failure.getClass().getSimpleName());
        }
    }
}
