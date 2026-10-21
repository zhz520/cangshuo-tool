package com.cangshuo.toolbox.membership.controller;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.membership.model.MembershipResponse;
import com.cangshuo.toolbox.membership.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户会员", description = "账号会员状态")
@RestController
@RequestMapping(value = "/api/v1/membership", produces = MediaType.APPLICATION_JSON_VALUE)
public class MembershipController {

    private final MembershipService service;

    public MembershipController(MembershipService service) {
        this.service = service;
    }

    @Operation(operationId = "getMembership", summary = "读取当前账号的会员状态")
    @GetMapping
    public ApiResponse<MembershipResponse> status(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.status(userId(jwt)), TraceIdFilter.traceId(request));
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
