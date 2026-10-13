package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.web.JsonBodyBudgetFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.concurrent.Semaphore;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MediaControllerTest {
    private final MediaService service = mock(MediaService.class);
    private MockMvc mvc() { return MockMvcBuilders.standaloneSetup(new MediaController(service)).setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new JsonBodyBudgetFilter(new ObjectMapper(),16384)).build(); }
    @Test void validatesBodyAndReturnsFixedLocalizedClientCodesBeforeService() throws Exception {
        for (String body : new String[]{"{}", "{\"text\":null}", "{\"text\":\" \"}", "{\"text\":\"" + "a".repeat(4097) + "\"}", "[]", "invalid"})
            mvc().perform(post("/api/v1/web-tools/short_video_parse/parse").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value(10001)).andExpect(header().string("Cache-Control","no-store"));
        verifyNoInteractions(service);
    }
    @Test void rejectsUnknownLengthOversizedBodyWithoutParsingIt() throws Exception {
        var request = new MockHttpServletRequest() { @Override public long getContentLengthLong() { return -1; } };
        request.setMethod("POST"); request.setContent("x".repeat(16385).getBytes()); var response = new MockHttpServletResponse();
        new JsonBodyBudgetFilter(new ObjectMapper(),16384).doFilter(request,response,(req,res) -> fail("must reject first"));
        assertEquals(413,response.getStatus()); assertTrue(response.getContentAsString().contains("40001"));
    }
    @Test void expiredAndMalformedHandlesReturnJsonErrorInsteadOfBinarySuccess() throws Exception {
        when(service.transfer(anyString(),anyInt(),any())).thenThrow(new ApiException(ApiError.MEDIA_RESULT_EXPIRED));
        mvc().perform(get("/api/v1/web-tools/short_video_parse/assets/" + "a".repeat(32) + "/0"))
                .andExpect(status().isGone()).andExpect(jsonPath("code").value(61004)).andExpect(header().string("Cache-Control","no-store"));
        mvc().perform(get("/api/v1/web-tools/short_video_parse/assets/bad/not-an-integer")).andExpect(status().isBadRequest());
    }
    @Test void streamedRangeAndZipHaveAccurateHeadersAndReleaseResources() throws Exception {
        var response = mock(ClassicHttpResponse.class); var slots = new Semaphore(0);
        var transfer = new MediaService.PreparedTransfer(new MediaHttp.Download(response,new ByteArrayInputStream(new byte[]{1,2}),"video/mp4",2,206,"bytes 0-1/10",MediaHttp.deadline(20)),"douyin_video_1.mp4",slots);
        when(service.transfer(anyString(),eq(0),eq("bytes=0-1"))).thenReturn(transfer);
        var mvc = mvc(); var request = mvc.perform(get("/api/v1/web-tools/short_video_parse/assets/" + "a".repeat(32) + "/0?download=true").header("Range","bytes=0-1"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(request)).andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 0-1/10"))
                .andExpect(header().string("Content-Length","2")).andExpect(header().string("Content-Type","video/mp4"))
                .andExpect(header().string("Content-Disposition","attachment; filename=\"douyin_video_1.mp4\""))
                .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(content().bytes(new byte[]{1,2}));
        assertEquals(1,slots.availablePermits()); verify(response).close();
        var archive = new MediaService.PreparedArchive(new byte[]{80,75},"douyin_album.zip",new Semaphore(0)); when(service.album(anyString(),eq("0,2"),anyString())).thenReturn(archive);
        var zip = mvc.perform(get("/api/v1/web-tools/short_video_parse/albums/" + "a".repeat(32) + "?selection=0,2")).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(zip)).andExpect(status().isOk()).andExpect(header().string("Content-Type","application/zip")).andExpect(content().bytes(archive.bytes()));
        assertEquals(1,archive.slots().availablePermits());
    }
}
