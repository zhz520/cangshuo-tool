package com.cangshuo.toolbox.common.ratelimit;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies per-identity fixed-window limits to /api/v1; registered by RateLimitConfiguration so MVC slice
 * tests do not pick it up as a scanned filter.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/register",
            "/api/v1/auth/refresh");
    private static final String HEALTH_PATH = "/api/v1/health";
    private final RateLimitProperties properties;
    private final RateLimitService limiter;
    private final ObjectMapper mapper;

    public RateLimitFilter(RateLimitProperties properties, RateLimitService limiter, ObjectMapper mapper) {
        this.properties = properties; this.limiter = limiter; this.mapper = mapper;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !properties.enabled() || !uri.startsWith("/api/v1/") || HEALTH_PATH.equals(uri)
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String identity = identity(request);
        boolean authAttempt = AUTH_PATHS.contains(request.getRequestURI()) ||
            ("DELETE".equals(request.getMethod()) && "/api/v1/auth/me".equals(request.getRequestURI()));
        int limit = authAttempt ? properties.maxAuthAttempts()
                : identity.startsWith("u:") ? properties.maxAuthenticatedRequests()
                : properties.maxAnonymousRequests();
        String key = (authAttempt ? "toolbox:rate:auth:" : "toolbox:rate:") + identity;
        RateLimitDecision decision = limiter.check(key, limit, properties.windowSeconds());
        response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
        if (!decision.allowed()) {
            response.setStatus(ApiError.TOO_MANY_REQUESTS.httpStatus().value());
            response.setHeader("Retry-After", Integer.toString(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(mapper.writeValueAsString(
                    ApiResponse.failure(ApiError.TOO_MANY_REQUESTS, TraceIdFilter.traceId(request))));
            return;
        }
        chain.doFilter(request, response);
    }

    private static String identity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Jwt jwt && jwt.getSubject() != null) return "u:" + jwt.getSubject();
            return "u:" + authentication.getName();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String candidate = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].strip();
        if (candidate == null || candidate.isBlank()
                || candidate.chars().anyMatch(Character::isISOControl)) candidate = "unknown";
        return "ip:" + (candidate.length() > 45 ? candidate.substring(0, 45) : candidate);
    }
}
