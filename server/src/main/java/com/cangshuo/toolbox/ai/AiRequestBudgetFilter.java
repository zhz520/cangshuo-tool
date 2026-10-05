package com.cangshuo.toolbox.ai;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds JSON before Jackson can expand it. Registered only on the AI text endpoint. */
class AiRequestBudgetFilter extends OncePerRequestFilter {
    static final int MAX_BYTES = 65_536;
    private final ObjectMapper mapper;
    AiRequestBudgetFilter(ObjectMapper mapper) { this.mapper = mapper; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (!"POST".equals(request.getMethod())) { chain.doFilter(request, response); return; }
        if (request.getContentLengthLong() > MAX_BYTES) { reject(request, response); return; }
        byte[] bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) { reject(request, response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var source = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return source.read(); }
                    @Override public int read(byte[] target, int offset, int length) { return source.read(target, offset, length); }
                    @Override public boolean isFinished() { return source.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        }, response);
    }
    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(413); response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), ApiResponse.failure(ApiError.FILE_TOO_LARGE, TraceIdFilter.traceId(request)));
    }
}
