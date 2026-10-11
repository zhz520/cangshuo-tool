package com.cangshuo.toolbox.statistics.controller;

import com.cangshuo.toolbox.auth.config.AuthConfiguration;
import com.cangshuo.toolbox.auth.config.SessionJwtConverter;
import com.cangshuo.toolbox.auth.service.RefreshTokenService;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.security.*;
import com.cangshuo.toolbox.statistics.model.StatisticsResponse;
import com.cangshuo.toolbox.statistics.service.StatisticsService;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminStatisticsController.class, properties = {
    "toolbox.auth.jwt-secret=1212121212121212121212121212121212121212121212121212121212121212", "toolbox.cors.allowed-origins="})
@Import({SecurityConfiguration.class, ApiSecurityErrorHandler.class, CorsConfiguration.class,
    AuthConfiguration.class, TraceIdFilter.class, SessionJwtConverter.class})
class AdminStatisticsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean StatisticsService service;
    @MockitoBean RefreshTokenService sessions;
    @Test void anonymousAndOrdinaryUserCannotReadStatistics() throws Exception {
        mvc.perform(get("/api/v1/admin/statistics")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(10002));
        mvc.perform(get("/api/v1/admin/statistics").with(jwt())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(10005));
        verifyNoInteractions(service);
    }
    @Test void administratorsReceiveSnapshotWithNoCacheAndTraceId() throws Exception {
        var today = LocalDate.of(2026, 10, 11);
        var now = Instant.parse("2026-10-11T00:00:00Z");
        var response = new StatisticsResponse(30, today.minusDays(29), today, "Asia/Shanghai", now, now,
            Map.of("users", 0L), List.of(), List.of(), List.of(), List.of(), new StatisticsResponse.Traffic(now, 0, 0, 0, 0, 0));
        when(service.snapshot(30)).thenReturn(response);
        mvc.perform(get("/api/v1/admin/statistics").with(jwt().authorities(() -> "ROLE_ADMIN")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().exists("X-Trace-Id")).andExpect(jsonPath("$.data.summary.users").value(0))
            .andExpect(jsonPath("$.data.days").value(30));
    }
    @Test void invalidWindowHasUnifiedError() throws Exception {
        when(service.snapshot(8)).thenThrow(new ApiException(ApiError.INVALID_ARGUMENT));
        mvc.perform(get("/api/v1/admin/statistics?days=8").with(jwt().authorities(() -> "ROLE_SUPER_ADMIN")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10001));
    }
}
