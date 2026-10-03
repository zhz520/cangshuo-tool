package com.cangshuo.toolbox.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Trace-Id";
    private static final String ATTRIBUTE_NAME = TraceIdFilter.class.getName() + ".traceId";
    private static final String MDC_KEY = "traceId";

    public static String traceId(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE_NAME);
        if (existing instanceof String traceId) {
            return traceId;
        }
        String traceId = UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(ATTRIBUTE_NAME, traceId);
        return traceId;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        String traceId = traceId(request);
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
