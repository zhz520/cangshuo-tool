package com.cangshuo.toolbox.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class AiRequestBudgetFilterTest {
    @Test void largeJsonIsRejectedBeforeController() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/v1/tools/ai-text");request.setContent(new byte[65537]);
        var response=new MockHttpServletResponse();var chain=new MockFilterChain();
        new AiRequestBudgetFilter(new ObjectMapper()).doFilter(request,response,chain);
        assertEquals(413,response.getStatus());assertNull(chain.getRequest());assertTrue(response.getContentAsString().contains("40001"));
    }
    @Test void boundedBodyIsReplayableForJackson() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/v1/tools/ai-text");request.setContent("{\"text\":\"input\"}".getBytes());
        var response=new MockHttpServletResponse();var chain=new MockFilterChain();
        new AiRequestBudgetFilter(new ObjectMapper()).doFilter(request,response,chain);
        assertEquals("{\"text\":\"input\"}",new String(chain.getRequest().getInputStream().readAllBytes()));
    }
}
