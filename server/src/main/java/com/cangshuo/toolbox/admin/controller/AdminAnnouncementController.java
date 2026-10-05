package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.admin.model.AdminAnnouncementRequest;
import com.cangshuo.toolbox.admin.model.AdminAnnouncementRow;
import com.cangshuo.toolbox.admin.model.AdminAnnouncementStatusRequest;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.service.AdminAnnouncementService;
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
@RequestMapping(value = "/api/v1/admin/announcements", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin announcements")
public class AdminAnnouncementController {
    private final AdminAnnouncementService service;
    public AdminAnnouncementController(AdminAnnouncementService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "List non-deleted announcements, newest first (drafts included)")
    public ApiResponse<List<AdminAnnouncementRow>> list(HttpServletRequest request) {
        return ApiResponse.success(service.list(), TraceIdFilter.traceId(request));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an announcement; enabled=false keeps it as a draft")
    public ApiResponse<AdminAnnouncementRow> create(@Valid @RequestBody AdminAnnouncementRequest body,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.create(body, context(request), adminId(jwt)), TraceIdFilter.traceId(request));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update an announcement")
    public ApiResponse<AdminAnnouncementRow> update(@PathVariable String id,
            @Valid @RequestBody AdminAnnouncementRequest body, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return ApiResponse.success(service.update(id, body, context(request), adminId(jwt)), TraceIdFilter.traceId(request));
    }

    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Publish or unpublish an announcement")
    public ApiResponse<AdminAnnouncementRow> status(@PathVariable String id,
            @Valid @RequestBody AdminAnnouncementStatusRequest body, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return ApiResponse.success(service.updateStatus(id, body.enabled(), context(request), adminId(jwt)),
                TraceIdFilter.traceId(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete an announcement")
    public ApiResponse<Void> delete(@PathVariable String id, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        service.delete(id, context(request), adminId(jwt));
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
