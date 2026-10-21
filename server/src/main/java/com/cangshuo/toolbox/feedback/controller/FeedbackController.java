package com.cangshuo.toolbox.feedback.controller;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.feedback.model.FeedbackRequest;
import com.cangshuo.toolbox.feedback.model.FeedbackResponse;
import com.cangshuo.toolbox.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/feedback", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "用户反馈")
public class FeedbackController {
    private final FeedbackService service;
    public FeedbackController(FeedbackService service) { this.service = service; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "提交用户反馈（需登录）")
    public ApiResponse<FeedbackResponse> submit(@Valid @RequestBody FeedbackRequest body,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.submit(userId(jwt), body), TraceIdFilter.traceId(request));
    }

    @GetMapping("/my")
    @Operation(summary = "获取我的反馈（按时间倒序，最多 50 条）")
    public ApiResponse<List<FeedbackResponse>> mine(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.mine(userId(jwt)), TraceIdFilter.traceId(request));
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
