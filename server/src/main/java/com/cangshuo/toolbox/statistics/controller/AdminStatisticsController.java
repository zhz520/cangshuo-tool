package com.cangshuo.toolbox.statistics.controller;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.statistics.model.StatisticsResponse;
import com.cangshuo.toolbox.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "后台运营统计", description = "仅管理员可读取的聚合运营统计")
public class AdminStatisticsController {
    private final StatisticsService service;
    public AdminStatisticsController(StatisticsService service) { this.service = service; }
    @GetMapping(value = "/api/v1/admin/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "adminStatistics", summary = "平台概览：7/30/90 日趋势、排行和累计统计")
    public ResponseEntity<ApiResponse<StatisticsResponse>> statistics(
            @RequestParam(defaultValue = "30") int days, HttpServletRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(service.snapshot(days), TraceIdFilter.traceId(request)));
    }
}
