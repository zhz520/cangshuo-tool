package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.model.AdminUserRow;
import com.cangshuo.toolbox.admin.model.AdminUserSessionResponse;
import com.cangshuo.toolbox.admin.model.AdminUserStatusRequest;
import com.cangshuo.toolbox.admin.model.AdminUserSyncEntryResponse;
import com.cangshuo.toolbox.admin.service.AdminInput;
import com.cangshuo.toolbox.admin.service.AdminUserService;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin users")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Page through user accounts; keyword matches email or nickname")
    public ApiResponse<PageResponse<AdminUserRow>> list(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            HttpServletRequest request) {
        return ApiResponse.success(service.list(page, pageSize, keyword, enabled), TraceIdFilter.traceId(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one user account with favorite/recent counts")
    public ApiResponse<AdminUserRow> detail(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.success(service.detail(id), TraceIdFilter.traceId(request));
    }

    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enable or disable a user; disabling revokes every refresh session immediately")
    public ApiResponse<AdminUserRow> status(@PathVariable String id,
            @Valid @RequestBody AdminUserStatusRequest body, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return ApiResponse.success(service.updateStatus(id, body.enabled(), context(request), adminId(jwt)),
                TraceIdFilter.traceId(request));
    }

    @GetMapping("/{id}/sessions")
    @Operation(summary = "List the latest refresh sessions with masked selectors")
    public ApiResponse<List<AdminUserSessionResponse>> sessions(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.success(service.sessions(id), TraceIdFilter.traceId(request));
    }

    @GetMapping("/{id}/sync")
    @Operation(summary = "List sync entity keys (favorites/recent) without exposing payloads")
    public ApiResponse<List<AdminUserSyncEntryResponse>> sync(@PathVariable String id,
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(50) int limit,
            HttpServletRequest request) {
        return ApiResponse.success(service.syncEntries(id, limit), TraceIdFilter.traceId(request));
    }

    private static AdminRequestContext context(HttpServletRequest request) {
        return new AdminRequestContext(AdminInput.clientIp(request), request.getRequestURI(), request.getMethod());
    }

    private static Long adminId(Jwt jwt) {
        try { return Long.parseLong(jwt.getSubject()); }
        catch (NumberFormatException exception) { return null; }
    }
}
