package com.cangshuo.toolbox.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {
    private final RateLimitService limiter = mock(RateLimitService.class);
    private final RateLimitFilter filter = new RateLimitFilter(
            new RateLimitProperties(true, 60, 2, 5, 1), limiter, new ObjectMapper());

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void skipsHealthOptionsAndNonApiRequests() {
        assertTrue(filter.shouldNotFilter(request("GET", "/actuator/health")));
        assertTrue(filter.shouldNotFilter(request("GET", "/api/v1/health")));
        assertTrue(filter.shouldNotFilter(request("OPTIONS", "/api/v1/tools")));
        assertTrue(filter.shouldNotFilter(request("GET", "/tools")));
        assertFalse(filter.shouldNotFilter(request("GET", "/api/v1/tools")));
        var disabled = new RateLimitFilter(new RateLimitProperties(false, 60, 2, 5, 1), limiter, new ObjectMapper());
        assertTrue(disabled.shouldNotFilter(request("GET", "/api/v1/tools")));
    }

    @Test void returnsTheUnified429EnvelopeWithRetryAfter() throws Exception {
        when(limiter.check(anyString(), eq(2), eq(60))).thenReturn(new RateLimitDecision(false, 2, 0, 42));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilterInternal(request("GET", "/api/v1/tools"), response, chain);
        assertEquals(429, response.getStatus());
        assertEquals("42", response.getHeader("Retry-After"));
        assertEquals("2", response.getHeader("X-RateLimit-Limit"));
        assertTrue(response.getContentAsString().contains("\"code\":10007"));
        assertNull(chain.getRequest());
    }

    @Test void usesTheJwtSubjectForAuthenticatedRequestsAndTheAddressOtherwise() throws Exception {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("7").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        when(limiter.check(anyString(), anyInt(), anyInt())).thenReturn(new RateLimitDecision(true, 5, 4, 60));
        filter.doFilterInternal(request("GET", "/api/v1/admin/tools"), new MockHttpServletResponse(), new MockFilterChain());
        verify(limiter).check("toolbox:rate:u:7", 5, 60);
        SecurityContextHolder.clearContext();
        var anonymous = request("GET", "/api/v1/tools");
        anonymous.setRemoteAddr("203.0.113.9");
        filter.doFilterInternal(anonymous, new MockHttpServletResponse(), new MockFilterChain());
        verify(limiter).check("toolbox:rate:ip:203.0.113.9", 2, 60);
        var authAttempt = request("POST", "/api/v1/auth/login");
        authAttempt.setRemoteAddr("203.0.113.9");
        filter.doFilterInternal(authAttempt, new MockHttpServletResponse(), new MockFilterChain());
        verify(limiter).check("toolbox:rate:auth:ip:203.0.113.9", 1, 60);
    }

    private static MockHttpServletRequest request(String method, String uri) {
        var request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }
}
