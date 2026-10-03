package com.cangshuo.toolbox.health.controller;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.health.model.HealthResponse;
import com.cangshuo.toolbox.health.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "服务可用性")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @Operation(operationId = "getHealth", summary = "服务健康检查",
            description = "汇总进程、磁盘、MySQL 及 Redis 连接状态；任一组件不可用时返回 503。", responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "服务可用",
                    headers = @Header(name = "X-Trace-Id", description = "服务端请求追踪标识",
                            schema = @Schema(type = "string", pattern = "^[0-9a-f]{32}$"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "服务暂不可用",
                    headers = @Header(name = "X-Trace-Id", description = "服务端请求追踪标识",
                            schema = @Schema(type = "string", pattern = "^[0-9a-f]{32}$")))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<HealthResponse>> health(HttpServletRequest request) {
        HealthResponse health = healthService.getHealth();
        String traceId = TraceIdFilter.traceId(request);
        if (!Status.UP.getCode().equals(health.status())) {
            return ResponseEntity.status(ApiError.SERVICE_UNAVAILABLE.httpStatus())
                    .body(ApiResponse.failure(ApiError.SERVICE_UNAVAILABLE, traceId));
        }
        return ResponseEntity.ok(ApiResponse.success(health, traceId));
    }
}
