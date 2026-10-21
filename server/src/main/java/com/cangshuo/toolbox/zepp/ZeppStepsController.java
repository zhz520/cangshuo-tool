package com.cangshuo.toolbox.zepp;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Zepp Life", description = "Zepp Life 网页工具；提交结果不确定时应先在客户端核对，不要重复提交。")
@RequestMapping("/api/v1/web-tools/zepp_steps")
public class ZeppStepsController {
    private final ZeppStepsService service;
    public ZeppStepsController(ZeppStepsService service) { this.service = service; }

    @GetMapping("/capabilities")
    @Operation(summary = "读取 Zepp Life 服务可用性、日期与步数范围")
    public ResponseEntity<ApiResponse<ZeppStepsService.Capabilities>> capabilities(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.capabilities(), TraceIdFilter.traceId(request)));
    }

    @PostMapping(value = "/submit", consumes = "application/json")
    @Operation(summary = "提交 Zepp Life 步数", description = "仅提交用户自有账号；60005 表示结果不确定，先核对 Zepp Life 后再决定是否重试。")
    public ResponseEntity<ApiResponse<ZeppStepsResponse>> submit(@Valid @RequestBody ZeppStepsRequest input,
                                                           HttpServletRequest request) {
        try {
            return ResponseEntity.ok()
                    .body(ApiResponse.success(service.submit(input), TraceIdFilter.traceId(request)));
        } catch (ApiException error) {
            var builder = ResponseEntity.status(error.error().httpStatus());
            if (error.error() == ApiError.ZEPP_RATE_LIMITED || error.error() == ApiError.ZEPP_ACCOUNT_COOLDOWN)
                builder.header("Retry-After", "60");
            return builder.body(ApiResponse.failure(error.error(), TraceIdFilter.traceId(request)));
        }
    }
}
