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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Cloud sync", description = "需用户登录；使用游标增量同步收藏、最近使用与偏好。")
@RequestMapping(value="/api/v1/sync",produces="application/json")
public class SyncController {
    private final SyncService service;
    public SyncController(SyncService service) { this.service=service; }
    @PostMapping(value="/push",consumes="application/json")
    @Operation(summary = "推送本机云同步变更")
    public ApiResponse<PushResult> push(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Push body,HttpServletRequest req) {
        return ApiResponse.success(service.push(Long.parseLong(jwt.getSubject()),body),TraceIdFilter.traceId(req));
    }
    @GetMapping("/pull")
    @Operation(summary = "按游标拉取云同步变更")
    public ApiResponse<PullResult> pull(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String cursor,HttpServletRequest req) {
        return ApiResponse.success(service.pull(Long.parseLong(jwt.getSubject()),cursor),TraceIdFilter.traceId(req));
    }
}
