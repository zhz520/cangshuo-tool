package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.admin.model.AdminFeedbackRow;
import com.cangshuo.toolbox.admin.model.AdminFeedbackUpdateRequest;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.service.AdminFeedbackService;
import com.cangshuo.toolbox.admin.service.AdminInput;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping(value = "/api/v1/admin/feedback", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin feedback")
public class AdminFeedbackController {
    private final AdminFeedbackService service;
    public AdminFeedbackController(AdminFeedbackService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Page through user feedback with keyword and status filters")
    public ApiResponse<PageResponse<AdminFeedbackRow>> list(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            HttpServletRequest request) {
        return ApiResponse.success(service.list(page, pageSize, keyword, status), TraceIdFilter.traceId(request));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Move feedback through PENDING/PROCESSING/RESOLVED and optionally reply once")
    public ApiResponse<AdminFeedbackRow> update(@PathVariable long id,
            @Valid @RequestBody AdminFeedbackUpdateRequest body, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        if (id <= 0) throw new com.cangshuo.toolbox.common.exception.ApiException(
                com.cangshuo.toolbox.common.exception.ApiError.INVALID_ARGUMENT);
        return ApiResponse.success(service.update(id, body, context(request), adminId(jwt)),
                TraceIdFilter.traceId(request));
    }

    private static AdminRequestContext context(HttpServletRequest request) {
        return new AdminRequestContext(AdminInput.clientIp(request), request.getRequestURI(), request.getMethod());
    }

    private static Long adminId(Jwt jwt) {
        try { return Long.parseLong(jwt.getSubject()); }
        catch (NumberFormatException exception) { return null; }
    }
}
