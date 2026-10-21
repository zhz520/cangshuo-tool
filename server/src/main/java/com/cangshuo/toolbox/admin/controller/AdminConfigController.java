package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.config.model.ConfigEntry;
import com.cangshuo.toolbox.config.model.UpdateConfigRequest;
import com.cangshuo.toolbox.config.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/config", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "后台系统配置")
public class AdminConfigController {

    private final ConfigService service;

    public AdminConfigController(ConfigService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "获取允许管理的业务配置列表")
    public ApiResponse<List<ConfigEntry>> list(HttpServletRequest request) {
        return ApiResponse.success(service.list(), TraceIdFilter.traceId(request));
    }

    @PutMapping(value = "/{key}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "更新白名单中的系统配置项")
    public ApiResponse<ConfigEntry> update(@PathVariable String key,
            @Valid @RequestBody UpdateConfigRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.update(key, body.value()), TraceIdFilter.traceId(request));
    }
}
