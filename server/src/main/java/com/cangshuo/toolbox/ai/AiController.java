package com.cangshuo.toolbox.ai;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tools/ai-text")
public class AiController {
    private final AiService service;
    public AiController(AiService service) { this.service = service; }
    @GetMapping("/status") public ApiResponse<AiService.Status> status(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.status(user(jwt)), TraceIdFilter.traceId(request));
    }
    @PostMapping public ApiResponse<AiResponse> complete(@Valid @RequestBody AiRequest body, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.complete(user(jwt), body), TraceIdFilter.traceId(request));
    }
    private static long user(Jwt jwt) {
        if (jwt == null || "admin".equals(jwt.getClaimAsString("kind"))) throw new ApiException(ApiError.UNAUTHENTICATED);
        try { long id = Long.parseLong(jwt.getSubject()); if (id <= 0) throw new NumberFormatException(); return id; }
        catch (NumberFormatException error) { throw new ApiException(ApiError.UNAUTHENTICATED); }
    }
}
