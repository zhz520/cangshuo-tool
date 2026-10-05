package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.admin.model.AdminCategoryResponse;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.model.AdminToolRequest;
import com.cangshuo.toolbox.admin.model.AdminToolResponse;
import com.cangshuo.toolbox.admin.model.AdminToolStatusRequest;
import com.cangshuo.toolbox.admin.service.AdminInput;
import com.cangshuo.toolbox.admin.service.AdminToolService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/tools", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin tools")
public class AdminToolController {
    private final AdminToolService service;
    public AdminToolController(AdminToolService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Page through catalog entries, including disabled and maintenance tools")
    public ApiResponse<PageResponse<AdminToolResponse>> list(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "status", required = false) String status,
            HttpServletRequest request) {
        return ApiResponse.success(service.list(page, pageSize, keyword, categoryCode, status),
                TraceIdFilter.traceId(request));
    }

    @GetMapping("/categories")
    @Operation(summary = "Read the category options used by the tool editor")
    public ApiResponse<List<AdminCategoryResponse>> categories(HttpServletRequest request) {
        return ApiResponse.success(service.categories(), TraceIdFilter.traceId(request));
    }

    @GetMapping("/{code}")
    @Operation(summary = "Read one catalog entry by code")
    public ApiResponse<AdminToolResponse> detail(@PathVariable String code, HttpServletRequest request) {
        return ApiResponse.success(service.detail(code), TraceIdFilter.traceId(request));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a catalog entry")
    public ApiResponse<AdminToolResponse> create(@Valid @RequestBody AdminToolRequest body,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.create(body, context(request), adminId(jwt)),
                TraceIdFilter.traceId(request));
    }

    @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace the editable fields of a catalog entry")
    public ApiResponse<AdminToolResponse> update(@PathVariable String code,
            @Valid @RequestBody AdminToolRequest body, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.update(code, body, context(request), adminId(jwt)),
                TraceIdFilter.traceId(request));
    }

    @PatchMapping(value = "/{code}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enable, disable or set maintenance for a catalog entry")
    public ApiResponse<AdminToolResponse> status(@PathVariable String code,
            @Valid @RequestBody AdminToolStatusRequest body, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return ApiResponse.success(service.updateStatus(code, body.status(), context(request), adminId(jwt)),
                TraceIdFilter.traceId(request));
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Soft-delete a catalog entry; the public catalog stops returning it")
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
