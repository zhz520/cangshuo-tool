package com.cangshuo.toolbox.sitelink.controller;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.sitelink.model.SiteLinkRequest;
import com.cangshuo.toolbox.sitelink.model.SiteLinkResponse;
import com.cangshuo.toolbox.sitelink.service.SiteLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "网站导航与投稿", description = "网站导航与投稿")
@RestController
@RequestMapping(value = "/api/v1/site-links", produces = MediaType.APPLICATION_JSON_VALUE)
public class SiteLinkController {

    private final SiteLinkService service;

    public SiteLinkController(SiteLinkService service) {
        this.service = service;
    }

    @Operation(operationId = "getSiteLinks", summary = "获取已通过的网站导航（匿名）")
    @GetMapping
    public ApiResponse<List<SiteLinkResponse>> list(
            @RequestParam(name = "limit", defaultValue = "100") @Min(1) @Max(100) int limit,
            HttpServletRequest request) {
        return ApiResponse.success(service.list(limit), TraceIdFilter.traceId(request));
    }

    @Operation(operationId = "submitSiteLink", summary = "投稿网站导航（需登录，提交后等待审核）")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SiteLinkResponse> submit(@Valid @RequestBody SiteLinkRequest body,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.submit(userId(jwt), body), TraceIdFilter.traceId(request));
    }

    private static long userId(Jwt jwt) {
        try {
            long id = Long.parseLong(jwt.getSubject());
            if (id <= 0) throw new ApiException(ApiError.UNAUTHENTICATED);
            return id;
        } catch (NumberFormatException exception) {
            throw new ApiException(ApiError.UNAUTHENTICATED);
        }
    }
}
