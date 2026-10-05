package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.admin.model.AdminRecommendationRequest;
import com.cangshuo.toolbox.admin.model.AdminRecommendationRow;
import com.cangshuo.toolbox.admin.model.AdminRecommendationStatusRequest;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.service.AdminInput;
import com.cangshuo.toolbox.admin.service.AdminRecommendationService;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/recommendations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin recommendations")
public class AdminRecommendationController {
    private final AdminRecommendationService service;
    public AdminRecommendationController(AdminRecommendationService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "List non-deleted recommendation slots with the computed active flag")
    public ApiResponse<List<AdminRecommendationRow>> list(HttpServletRequest request) {
        return ApiResponse.success(service.list(), TraceIdFilter.traceId(request));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a recommendation slot; set toolCode or linkUrl, never both")
    public ApiResponse<AdminRecommendationRow> create(@Valid @RequestBody AdminRecommendationRequest body,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.create(body, context(request), adminId(jwt)),
                TraceIdFilter.traceId(request));
    }

    @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a recommendation slot")
    public ApiResponse<AdminRecommendationRow> update(@PathVariable String code,
            @Valid @RequestBody AdminRecommendationRequest body, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return ApiResponse.success(service.update(code, body, context(request), adminId(jwt)),
                TraceIdFilter.traceId(request));
    }

    @PatchMapping(value = "/{code}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enable or disable a recommendation slot")
    public ApiResponse<AdminRecommendationRow> status(@PathVariable String code,
            @Valid @RequestBody AdminRecommendationStatusRequest body, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return ApiResponse.success(service.updateStatus(code, body.enabled(), context(request), adminId(jwt)),
                TraceIdFilter.traceId(request));
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Soft-delete a recommendation slot")
    public ApiResponse<Void> delete(@PathVariable String code, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        service.delete(code, context(request), adminId(jwt));
        return ApiResponse.success(null, TraceIdFilter.traceId(request));
    }

    private static AdminRequestContext context(HttpServletRequest request) {
        return new AdminRequestContext(AdminInput.clientIp(request), request.getRequestURI(), request.getMethod());
    }

    private static Long adminId(Jwt jwt) {
        try { return Long.parseLong(jwt.getSubject()); }
        catch (NumberFormatException exception) { return null; }
    }
}
