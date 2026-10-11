package com.cangshuo.toolbox.app;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "App", description = "客户端版本与更新信息")
@RestController
@RequestMapping("/api/v1/app")
public class AppVersionController {

    private final long versionCode;
    private final String versionName;
    private final String url;
    private final String notes;

    public AppVersionController(
            @Value("${toolbox.app.latest-version-code:1}") long versionCode,
            @Value("${toolbox.app.latest-version-name:0.1.0}") String versionName,
            @Value("${toolbox.app.update-url:}") String url,
            @Value("${toolbox.app.update-notes:}") String notes) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.url = url;
        this.notes = notes;
    }

    @Operation(operationId = "getAppVersion", summary = "获取最新客户端版本信息（匿名）",
            description = "版本号、下载地址与更新说明来自服务端配置，便于不发版时更新渠道说明。")
    @GetMapping(value = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AppVersionResponse> version(HttpServletRequest request) {
        return ApiResponse.success(new AppVersionResponse(versionCode, versionName, url, notes),
                TraceIdFilter.traceId(request));
    }
}
