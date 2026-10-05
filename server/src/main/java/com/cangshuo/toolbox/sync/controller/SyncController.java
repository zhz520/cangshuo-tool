package com.cangshuo.toolbox.sync.controller;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.sync.model.SyncModels.*;
import com.cangshuo.toolbox.sync.service.SyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/v1/sync",produces="application/json")
public class SyncController {
    private final SyncService service;
    public SyncController(SyncService service) { this.service=service; }
    @PostMapping(value="/push",consumes="application/json")
    public ApiResponse<PushResult> push(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Push body,HttpServletRequest req) {
        return ApiResponse.success(service.push(Long.parseLong(jwt.getSubject()),body),TraceIdFilter.traceId(req));
    }
    @GetMapping("/pull")
    public ApiResponse<PullResult> pull(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String cursor,HttpServletRequest req) {
        return ApiResponse.success(service.pull(Long.parseLong(jwt.getSubject()),cursor),TraceIdFilter.traceId(req));
    }
}
