package com.cangshuo.toolbox.ai;

import com.cangshuo.toolbox.common.exception.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiServiceTest {
    static AiProperties properties(boolean enabled) { return new AiProperties(enabled,"https://example.com/v1/chat/completions","test-key","test-model","max_completion_tokens",1024,1,2,1,"Test"); }
    @Test void inputValidationAndTaskInstructions() {
        assertTrue(AiService.instruction(new AiRequest("TRANSLATE","hello","zh")).contains("Simplified Chinese"));
        assertTrue(AiService.instruction(new AiRequest("SUMMARIZE","hello",null)).contains("summary"));
        for (var request : new AiRequest[]{new AiRequest("BAD","hello",null),new AiRequest("REWRITE","",null),
            new AiRequest("TRANSLATE","hello","fr"),new AiRequest("REWRITE","x".repeat(8001),null),new AiRequest("REWRITE","x\u0000",null)})
            assertThrows(ApiException.class,()->AiService.instruction(request));
    }
    @Test void disabledNeverReservesOrCallsProvider() {
        var provider=mock(AiProvider.class);var quota=mock(AiQuotaRepository.class);
        var service=new AiService(properties(false),provider,quota);
        assertEquals(ApiError.SERVICE_UNAVAILABLE,assertThrows(ApiException.class,()->service.complete(1,new AiRequest("REWRITE","input",null))).error());
        verifyNoInteractions(provider,quota);assertFalse(service.status(1).enabled());
    }
    @Test void quotaRejectedBeforeProvider() {
        var provider=mock(AiProvider.class);var quota=mock(AiQuotaRepository.class);
        var service=new AiService(properties(true),provider,quota);
        assertEquals(ApiError.AI_QUOTA_EXCEEDED,assertThrows(ApiException.class,()->service.complete(1,new AiRequest("REWRITE","input",null))).error());
        verifyNoInteractions(provider);
    }
    @Test void providerFailureConsumesAttemptButReleasesConcurrency() {
        var provider=mock(AiProvider.class);var quota=mock(AiQuotaRepository.class);when(quota.reserve(1,2)).thenReturn(true);
        when(provider.complete(anyString(),anyString())).thenThrow(new ApiException(ApiError.AI_PROVIDER_ERROR)).thenReturn(new AiResponse("ok","model",false,null,null));
        var service=new AiService(properties(true),provider,quota);
        assertThrows(ApiException.class,()->service.complete(1,new AiRequest("REWRITE","input",null)));
        assertEquals("ok",service.complete(1,new AiRequest("REWRITE","input",null)).text());verify(quota,times(2)).reserve(1,2);
    }
    @Test void concurrentRejectionDoesNotConsumeQuota() throws Exception {
        var entered=new CountDownLatch(1);var released=new CountDownLatch(1);
        var provider=mock(AiProvider.class);var quota=mock(AiQuotaRepository.class);when(quota.reserve(1,2)).thenReturn(true);
        when(provider.complete(anyString(),anyString())).thenAnswer(call->{entered.countDown();released.await(3,TimeUnit.SECONDS);return new AiResponse("ok","model",false,null,null);});
        var service=new AiService(properties(true),provider,quota);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var first=executor.submit(()->service.complete(1,new AiRequest("REWRITE","input",null)));
            try {assertTrue(entered.await(2,TimeUnit.SECONDS));assertEquals(ApiError.TOO_MANY_REQUESTS,
                assertThrows(ApiException.class,()->service.complete(2,new AiRequest("REWRITE","input",null))).error());verify(quota,never()).reserve(2,2);}
            finally{released.countDown();}first.get(2,TimeUnit.SECONDS);
        }
    }
    @Test void configRequiresHttpsAndNeverLeaksKeys() {
        assertEquals("AiProperties[redacted]",properties(true).toString());
        assertThrows(IllegalStateException.class,()->new AiProperties(true,"http://example.com/v1/chat/completions","secret","model",null,0,0,0,0,null));
        assertThrows(IllegalStateException.class,()->new AiProperties(true,"https://example.com/v1/chat/completions?secret=x","secret","model",null,0,0,0,0,null));
    }
}
