package com.cangshuo.toolbox.auth.controller;

import com.cangshuo.toolbox.auth.model.AuthResponse;
import com.cangshuo.toolbox.auth.model.LoginRequest;
import com.cangshuo.toolbox.auth.model.RegisterRequest;
import com.cangshuo.toolbox.auth.model.UserResponse;
import com.cangshuo.toolbox.auth.service.AuthService;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService service;
    private final com.cangshuo.toolbox.auth.service.RefreshTokenService sessions;
    public AuthController(AuthService service, com.cangshuo.toolbox.auth.service.RefreshTokenService sessions) {
        this.service = service; this.sessions = sessions;
    }

    @org.springframework.web.bind.annotation.DeleteMapping(value="/me", consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Permanently delete the current account after password and email confirmation")
    public ApiResponse<Void> deleteAccount(@AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody com.cangshuo.toolbox.auth.model.DeleteAccountRequest body, HttpServletRequest request) {
        if (jwt == null || "admin".equals(jwt.getClaimAsString("kind")))
            throw new com.cangshuo.toolbox.common.exception.ApiException(com.cangshuo.toolbox.common.exception.ApiError.UNAUTHENTICATED);
        service.deleteAccount(jwt.getSubject(), body);
        return ApiResponse.success(null, TraceIdFilter.traceId(request));
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rotate a refresh token and issue a new access token")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody com.cangshuo.toolbox.auth.model.RefreshRequest body,
                                            HttpServletRequest request) {
        return ApiResponse.success(sessions.refresh(body.refreshToken()), TraceIdFilter.traceId(request));
    }

    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Revoke the current refresh session")
    public ApiResponse<Void> logout(@Valid @RequestBody com.cangshuo.toolbox.auth.model.RefreshRequest body,
                                   HttpServletRequest request) {
        sessions.logout(body.refreshToken());
        return ApiResponse.success(null, TraceIdFilter.traceId(request));
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register with email and password")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.register(body), TraceIdFilter.traceId(request));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Sign in with email and password")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.login(body), TraceIdFilter.traceId(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Read the current active account")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.success(service.currentUser(jwt.getSubject()), TraceIdFilter.traceId(request));
    }

    @org.springframework.web.bind.annotation.PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update the current account nickname")
    public ApiResponse<UserResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody com.cangshuo.toolbox.auth.model.ProfileRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.updateProfile(jwt.getSubject(), body), TraceIdFilter.traceId(request));
    }
}
