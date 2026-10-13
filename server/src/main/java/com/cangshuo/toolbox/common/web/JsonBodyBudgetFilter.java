package com.cangshuo.toolbox.common.web;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded buffering before Jackson, including unknown-length/chunked POST bodies. */
public class JsonBodyBudgetFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    private final int maxBytes;
    public JsonBodyBudgetFilter(ObjectMapper mapper, int maxBytes) { this.mapper = mapper; this.maxBytes = maxBytes; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        if (!"POST".equals(request.getMethod())) { chain.doFilter(request, response); return; }
        byte[] bytes = request.getContentLengthLong() > maxBytes ? null : request.getInputStream().readNBytes(maxBytes + 1);
        if (bytes == null || bytes.length > maxBytes) {
            response.setStatus(413); response.setContentType("application/json");
            mapper.writeValue(response.getOutputStream(), ApiResponse.failure(ApiError.FILE_TOO_LARGE, TraceIdFilter.traceId(request)));
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var stream = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return stream.read(); }
                    @Override public int read(byte[] out, int offset, int length) { return stream.read(out, offset, length); }
                    @Override public boolean isFinished() { return stream.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        }, response);
    }
}
