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
@Tag(name = "后台文件存储")
public class AdminStorageController {
    private final AdminStorageService service;
    public AdminStorageController(AdminStorageService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "获取文件存储状态与限制（停用时写入返回 503/10008）")
    public ApiResponse<AdminStorageStatusResponse> status(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.status(adminId(jwt)), TraceIdFilter.traceId(request));
    }

    @PostMapping(value = "/objects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "上传文件（服务端生成对象键并计算 SHA-256）")
    public ApiResponse<AdminStoredObjectResponse> upload(@RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.upload(file, context(request), adminId(jwt)), TraceIdFilter.traceId(request));
    }

    @GetMapping("/objects/presigned")
    @Operation(summary = "获取已有文件的临时下载链接")
    public ApiResponse<AdminPresignedResponse> presigned(@RequestParam("key") String key,
            @RequestParam(name = "expirySeconds", required = false) Integer expirySeconds,
            HttpServletRequest request) {
        return ApiResponse.success(service.presign(key, expirySeconds), TraceIdFilter.traceId(request));
    }

    @DeleteMapping("/objects")
    @Operation(summary = "删除存储文件（不存在时返回 404/10006）")
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
