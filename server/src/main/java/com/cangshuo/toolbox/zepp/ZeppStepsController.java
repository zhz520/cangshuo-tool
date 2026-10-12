package com.cangshuo.toolbox.zepp;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/web-tools/zepp_steps")
public class ZeppStepsController {
    private final ZeppStepsService service;
    public ZeppStepsController(ZeppStepsService service) { this.service = service; }

    @GetMapping("/capabilities")
    public ResponseEntity<ApiResponse<ZeppStepsService.Capabilities>> capabilities(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.capabilities(), TraceIdFilter.traceId(request)));
    }

    @PostMapping(value = "/submit", consumes = "application/json")
    public ResponseEntity<? extends ApiResponse<?>> submit(@Valid @RequestBody ZeppStepsRequest input,
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
