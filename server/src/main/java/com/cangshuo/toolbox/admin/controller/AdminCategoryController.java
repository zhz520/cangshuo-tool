package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.admin.model.AdminCategoryRequest;
import com.cangshuo.toolbox.admin.model.AdminCategoryRow;
import com.cangshuo.toolbox.admin.model.AdminCategoryStatusRequest;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.service.AdminCategoryService;
import com.cangshuo.toolbox.admin.service.AdminInput;
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
@RequestMapping(value = "/api/v1/admin/categories", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "后台分类管理")
public class AdminCategoryController {
    private final AdminCategoryService service;
    public AdminCategoryController(AdminCategoryService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "获取分类列表及未删除工具数量")
    public ApiResponse<List<AdminCategoryRow>> list(HttpServletRequest request) {
        return ApiResponse.success(service.list(), TraceIdFilter.traceId(request));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建工具分类")
    public ApiResponse<AdminCategoryRow> create(@Valid @RequestBody AdminCategoryRequest body,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.create(body, context(request), adminId(jwt)), TraceIdFilter.traceId(request));
    }

    @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "更新工具分类")
    public ApiResponse<AdminCategoryRow> update(@PathVariable String code,
            @Valid @RequestBody AdminCategoryRequest body, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return ApiResponse.success(service.update(code, body, context(request), adminId(jwt)), TraceIdFilter.traceId(request));
    }

    @PatchMapping(value = "/{code}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "启用或停用分类（停用后隐藏所属工具）")
    public ApiResponse<AdminCategoryRow> status(@PathVariable String code,
            @Valid @RequestBody AdminCategoryStatusRequest body, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return ApiResponse.success(service.updateStatus(code, body.enabled(), context(request), adminId(jwt)),
                TraceIdFilter.traceId(request));
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "软删除空分类（仍有工具时返回 409/30007）")
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
