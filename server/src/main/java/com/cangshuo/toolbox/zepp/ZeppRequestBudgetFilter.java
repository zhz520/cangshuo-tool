package com.cangshuo.toolbox.zepp;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies before JSON parsing, including chunked requests; no body is logged. */
class ZeppRequestBudgetFilter extends OncePerRequestFilter {
    static final int MAX_BYTES = 4096;
    private final ObjectMapper mapper;
    ZeppRequestBudgetFilter(ObjectMapper mapper) { this.mapper = mapper; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws IOException, ServletException {
        if (!"POST".equals(request.getMethod())) { chain.doFilter(request, response); return; }
        response.setHeader("Cache-Control", "no-store");
        byte[] bytes = request.getContentLengthLong() > MAX_BYTES ? null
                : request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (bytes == null || bytes.length > MAX_BYTES) {
            response.setStatus(413); response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            mapper.writeValue(response.getOutputStream(), ApiResponse.failure(ApiError.FILE_TOO_LARGE,
                    TraceIdFilter.traceId(request)));
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
            @Override public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }
}
