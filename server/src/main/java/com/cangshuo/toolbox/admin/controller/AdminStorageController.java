package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.admin.model.AdminPresignedResponse;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.model.AdminStorageStatusResponse;
import com.cangshuo.toolbox.admin.model.AdminStoredObjectResponse;
import com.cangshuo.toolbox.admin.service.AdminInput;
import com.cangshuo.toolbox.admin.service.AdminStorageService;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value = "/api/v1/admin/storage", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin storage")
public class AdminStorageController {
    private final AdminStorageService service;
    public AdminStorageController(AdminStorageService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Read object storage status and limits; enabled=false means every write returns 503/10008")
    public ApiResponse<AdminStorageStatusResponse> status(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.status(adminId(jwt)), TraceIdFilter.traceId(request));
    }

    @PostMapping(value = "/objects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload one bounded object; the server generates the key and computes SHA-256")
    public ApiResponse<AdminStoredObjectResponse> upload(@RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.upload(file, context(request), adminId(jwt)), TraceIdFilter.traceId(request));
    }

    @GetMapping("/objects/presigned")
    @Operation(summary = "Create a presigned GET URL for an existing object")
    public ApiResponse<AdminPresignedResponse> presigned(@RequestParam("key") String key,
            @RequestParam(name = "expirySeconds", required = false) Integer expirySeconds,
            HttpServletRequest request) {
        return ApiResponse.success(service.presign(key, expirySeconds), TraceIdFilter.traceId(request));
    }

    @DeleteMapping("/objects")
    @Operation(summary = "Delete one object; missing objects return 404/10006")
    public ApiResponse<Void> delete(@RequestParam("key") String key, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        service.delete(key, context(request), adminId(jwt));
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
