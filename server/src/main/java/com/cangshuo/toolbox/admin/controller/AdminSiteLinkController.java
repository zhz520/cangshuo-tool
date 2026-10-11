package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.admin.model.AdminSiteLinkRow;
import com.cangshuo.toolbox.admin.model.AdminSiteLinkUpdateRequest;
import com.cangshuo.toolbox.admin.service.AdminSiteLinkService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/site-links", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin site links")
public class AdminSiteLinkController {

    private final AdminSiteLinkService service;

    public AdminSiteLinkController(AdminSiteLinkService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Page through navigation submissions with keyword and status filters")
    public ApiResponse<PageResponse<AdminSiteLinkRow>> list(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            HttpServletRequest request) {
        return ApiResponse.success(service.list(page, pageSize, status, keyword), TraceIdFilter.traceId(request));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Approve or reject a navigation submission")
    public ApiResponse<AdminSiteLinkRow> update(@PathVariable long id,
            @Valid @RequestBody AdminSiteLinkUpdateRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.update(id, body.status()), TraceIdFilter.traceId(request));
    }
}
