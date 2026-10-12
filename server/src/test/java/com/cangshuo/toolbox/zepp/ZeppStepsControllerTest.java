package com.cangshuo.toolbox.zepp;

import com.cangshuo.toolbox.common.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ZeppStepsControllerTest {
    private final ZeppStepsService service=mock(ZeppStepsService.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private MockMvc mvc(){return MockMvcBuilders.standaloneSetup(new ZeppStepsController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new ZeppRequestBudgetFilter(mapper)).build();}
    @Test void rejectsFractionalStringOutOfRangeAndMissingConsentAtApiBoundary() throws Exception {
        for(String steps:new String[]{"1.5","\"10000\"","0","50001","2147483648","null"})
            mvc().perform(post("/api/v1/web-tools/zepp_steps/submit").contentType("application/json")
                    .content("{\"account\":\"test@example.com\",\"password\":\"password\",\"steps\":"+steps+",\"consent\":true}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value(10001))
                    .andExpect(header().string("Cache-Control","no-store"));
        for(String consent:new String[]{"false","null","\"true\"","1"})
            mvc().perform(post("/api/v1/web-tools/zepp_steps/submit").contentType("application/json")
                    .content("{\"account\":\"test@example.com\",\"password\":\"password\",\"steps\":10000,\"consent\":"+consent+"}"))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsChunkedOversizedInputBeforeJsonParsing() throws Exception {
        var filter=new ZeppRequestBudgetFilter(mapper);var request=new MockHttpServletRequest("POST","/api/v1/web-tools/zepp_steps/submit");
        request.setContent(new byte[4097]);var response=new MockHttpServletResponse();var chain=new MockFilterChain();
        filter.doFilter(request,response,chain);assertEquals(413,response.getStatus());assertNull(chain.getRequest());
    }
    @Test void providerCredentialFailureIs422AndDoesNotInvalidateTheToolboxLogin() throws Exception {
        when(service.submit(any())).thenThrow(new ApiException(ApiError.ZEPP_INVALID_CREDENTIALS));
        mvc().perform(post("/api/v1/web-tools/zepp_steps/submit").contentType("application/json")
                .content("{\"account\":\"test@example.com\",\"password\":\"password\",\"steps\":10000,\"consent\":true}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("code").value(60001))
                .andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void cooldownReturnsRetryAfterAndNoRawCredentials() throws Exception {
        when(service.submit(any())).thenThrow(new ApiException(ApiError.ZEPP_ACCOUNT_COOLDOWN));
        mvc().perform(post("/api/v1/web-tools/zepp_steps/submit").contentType("application/json")
                .content("{\"account\":\"test@example.com\",\"password\":\"password\",\"steps\":10000,\"consent\":true}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"))
                .andExpect(jsonPath("code").value(60007));
    }
}
