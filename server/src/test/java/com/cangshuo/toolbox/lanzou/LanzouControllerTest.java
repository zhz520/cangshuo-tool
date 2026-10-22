package com.cangshuo.toolbox.lanzou;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.web.JsonBodyBudgetFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LanzouControllerTest {
    private final LanzouService service = mock(LanzouService.class);
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new LanzouController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new JsonBodyBudgetFilter(new ObjectMapper(), 8192)).build();
    }
    @Test void capabilitiesAreNoStoreAndValidationDoesNotCallUpstream() throws Exception {
        when(service.capabilities()).thenReturn(new LanzouService.Capabilities(true, 4096, 64, 180, 8, 2,
                "OFFICIAL_PAGE_FALLBACK"));
        mvc().perform(get("/api/v1/web-tools/lanzou_direct/capabilities"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.available").value(true))
                .andExpect(jsonPath("data.verificationMode").value("OFFICIAL_PAGE_FALLBACK"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc().perform(post("/api/v1/web-tools/lanzou_direct/parse").contentType("application/json")
                        .content("{\"text\":\"" + "x".repeat(4097) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value(10001));
        verify(service, never()).parse(any(), any(), any());
    }
    @Test void parseReturnsDirectResultAndMapsVerificationErrorsForClientLocalization() throws Exception {
        when(service.parse(anyString(), any(), anyString())).thenReturn(new LanzouService.Result(
                "https://lanzouf.com/i1", "a.zip", "1 MB", "DIRECT", "https://cdn.example.com/a.zip",
                "https://download.lanzouf.com/file/x", false, false, false, Instant.parse("2026-10-22T01:00:00Z"), 180));
        mvc().perform(post("/api/v1/web-tools/lanzou_direct/parse").contentType("application/json")
                        .content("{\"text\":\"https://lanzouf.com/i1\",\"password\":\"1234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.state").value("DIRECT"))
                .andExpect(jsonPath("data.directUrl").value("https://cdn.example.com/a.zip"));
        when(service.parse(anyString(), any(), anyString())).thenThrow(new ApiException(ApiError.LANZOU_VERIFICATION_REQUIRED));
        mvc().perform(post("/api/v1/web-tools/lanzou_direct/parse").contentType("application/json")
                        .content("{\"text\":\"https://lanzouf.com/i1\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("code").value(62005));
    }
}
