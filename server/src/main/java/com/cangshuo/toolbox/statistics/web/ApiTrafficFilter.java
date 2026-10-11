package com.cangshuo.toolbox.statistics.web;

import com.cangshuo.toolbox.statistics.service.ApiTrafficMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiTrafficFilter extends OncePerRequestFilter {
    private final ApiTrafficMeter meter;
    public ApiTrafficFilter(ApiTrafficMeter meter) { this.meter = meter; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/v1/") || path.equals("/api/v1/health") || "OPTIONS".equals(request.getMethod());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        boolean failed = true;
        try { chain.doFilter(request, response); failed = false; }
        finally { meter.completed(failed ? 500 : response.getStatus(), System.nanoTime() - start); }
    }
}
