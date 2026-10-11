package com.cangshuo.toolbox.app;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.config.service.ConfigService;
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

    private final ConfigService config;
    private final long fallbackVersionCode;
    private final String fallbackVersionName;
    private final String fallbackUrl;
    private final String fallbackNotes;

    public AppVersionController(ConfigService config,
            @Value("${toolbox.app.latest-version-code:1}") long fallbackVersionCode,
            @Value("${toolbox.app.latest-version-name:0.1.0}") String fallbackVersionName,
            @Value("${toolbox.app.update-url:}") String fallbackUrl,
            @Value("${toolbox.app.update-notes:}") String fallbackNotes) {
        this.config = config;
        this.fallbackVersionCode = fallbackVersionCode;
        this.fallbackVersionName = fallbackVersionName;
        this.fallbackUrl = fallbackUrl;
        this.fallbackNotes = fallbackNotes;
    }

    @Operation(operationId = "getAppVersion", summary = "获取最新客户端版本信息（匿名）",
            description = "版本号、下载地址与更新说明来自服务端配置，便于不发版时更新渠道说明。")
    @GetMapping(value = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AppVersionResponse> version(HttpServletRequest request) {
        long versionCode = config.longValue(ConfigService.APP_LATEST_VERSION_CODE).orElse(fallbackVersionCode);
        String versionName = config.value(ConfigService.APP_LATEST_VERSION_NAME)
                .filter(value -> !value.isBlank()).orElse(fallbackVersionName);
        String url = config.value(ConfigService.APP_UPDATE_URL).orElse(fallbackUrl);
        String notes = config.value(ConfigService.APP_UPDATE_NOTES).orElse(fallbackNotes);
        return ApiResponse.success(new AppVersionResponse(versionCode, versionName, url, notes),
                TraceIdFilter.traceId(request));
    }
}
