package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.admin.model.AdminAuthResponse;
import com.cangshuo.toolbox.admin.model.AdminLoginRequest;
import com.cangshuo.toolbox.admin.model.AdminProfileResponse;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.service.AdminAuthService;
import com.cangshuo.toolbox.admin.service.AdminInput;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "后台管理员认证")
public class AdminAuthController {
    private final AdminAuthService service;
    public AdminAuthController(AdminAuthService service) { this.service = service; }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "管理员账号密码登录")
    public ApiResponse<AdminAuthResponse> login(@Valid @RequestBody AdminLoginRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.login(body, context(request)), TraceIdFilter.traceId(request));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前管理员资料")
    public ApiResponse<AdminProfileResponse> me(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.current(jwt.getSubject()), TraceIdFilter.traceId(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "管理员退出登录（记录退出事件，客户端丢弃令牌）")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        service.logout(jwt.getSubject(), context(request));
        return ApiResponse.success(null, TraceIdFilter.traceId(request));
    }

    private static AdminRequestContext context(HttpServletRequest request) {
        return new AdminRequestContext(AdminInput.clientIp(request), request.getRequestURI(), request.getMethod());
    }
}
