package com.cangshuo.toolbox.ai;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "AI text", description = "需用户登录；服务与额度由后台配置。")
@RequestMapping("/api/v1/tools/ai-text")
public class AiController {
    private final AiService service;
    public AiController(AiService service) { this.service = service; }
    @Operation(summary = "读取 AI 文本服务状态与当前账号额度")
    @GetMapping("/status") public ApiResponse<AiService.Status> status(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.status(user(jwt)), TraceIdFilter.traceId(request));
    }
    @Operation(summary = "执行 AI 文本任务")
    @PostMapping public ApiResponse<AiResponse> complete(@Valid @RequestBody AiRequest body, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.complete(user(jwt), body), TraceIdFilter.traceId(request));
    }
    private static long user(Jwt jwt) {
        if (jwt == null || "admin".equals(jwt.getClaimAsString("kind"))) throw new ApiException(ApiError.UNAUTHENTICATED);
        try { long id = Long.parseLong(jwt.getSubject()); if (id <= 0) throw new NumberFormatException(); return id; }
        catch (NumberFormatException error) { throw new ApiException(ApiError.UNAUTHENTICATED); }
    }
}
