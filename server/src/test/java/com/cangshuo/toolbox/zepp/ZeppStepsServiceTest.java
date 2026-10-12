package com.cangshuo.toolbox.zepp;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.ratelimit.*;
import com.cangshuo.toolbox.tool.repository.ToolUsageRepository;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ZeppStepsServiceTest {
    @Test void phoneEmailAndMaskRulesAreConsistentAndForeignNumbersRejected() {
        assertEquals("+8613812345678",ZeppStepsService.normalize(" 13812345678 "));
        assertEquals("Name@example.com",ZeppStepsService.normalize("Name@EXAMPLE.COM"));
        assertEquals("Na***@example.com",ZeppStepsService.mask("Name@example.com"));
        for(String account : new String[]{"xiaomi123","+12025550111","a@localhost","a\n@example.com","a..b@example.com"})
            assertThrows(ApiException.class,()->ZeppStepsService.normalize(account));
    }
    @Test void catalogOffAndMissingConfigurationDoNotContactZepp() {
        var provider=mock(ZeppProvider.class);var catalog=mock(ToolUsageRepository.class);var limiter=mock(RateLimitService.class);
        var service=new ZeppStepsService(ZeppHttpProviderTest.properties(),provider,catalog,limiter);
        assertFalse(service.capabilities().available());
        assertEquals(ApiError.SERVICE_UNAVAILABLE,assertThrows(ApiException.class,()->service.submit(request())).error());
        var notConfigured=new ZeppStepsService(new ZeppProperties(true,"",""),provider,catalog,limiter);
        assertEquals(ApiError.ZEPP_NOT_CONFIGURED,assertThrows(ApiException.class,()->notConfigured.submit(request())).error());
        verifyNoInteractions(provider,limiter);
    }
    @Test void saltedAccountCooldownDoesNotStoreCredentialsAndSuccessNeverClaimsWechatVerification() {
        var provider=mock(ZeppProvider.class);var catalog=mock(ToolUsageRepository.class);var limiter=mock(RateLimitService.class);
        when(catalog.visible("zepp_steps")).thenReturn(true);
        when(limiter.check(anyString(),eq(1),eq(60))).thenReturn(new RateLimitDecision(true,1,0,60),new RateLimitDecision(false,1,0,60));
        when(provider.submit("test@example.com"," secret+& ",10000)).thenReturn(
                new ZeppProvider.Submission(LocalDate.of(2026,10,12),Instant.parse("2026-10-12T12:00:00Z"),true));
        var service=new ZeppStepsService(ZeppHttpProviderTest.properties(),provider,catalog,limiter);
        var response=service.submit(request());
        assertEquals("ACCEPTED",response.status());assertFalse(response.wechatVerified());assertEquals("te***@example.com",response.accountMasked());
        assertEquals(ApiError.ZEPP_ACCOUNT_COOLDOWN,assertThrows(ApiException.class,()->service.submit(request())).error());
        verify(provider,times(1)).submit(anyString(),anyString(),anyInt());
        var key=org.mockito.ArgumentCaptor.forClass(String.class);verify(limiter,times(2)).check(key.capture(),eq(1),eq(60));
        assertEquals(key.getAllValues().get(0),key.getAllValues().get(1));assertTrue(key.getValue().matches("toolbox:rate:zepp:[0-9a-f]{64}"));
        assertFalse(request().toString().contains("secret"));assertFalse(ZeppHttpProviderTest.properties().toString().contains("test-key"));
    }
    @Test void invalidInputAndMissingConsentNeverConsumeCooldown() {
        var provider=mock(ZeppProvider.class);var catalog=mock(ToolUsageRepository.class);var limiter=mock(RateLimitService.class);
        when(catalog.visible("zepp_steps")).thenReturn(true);
        var service=new ZeppStepsService(ZeppHttpProviderTest.properties(),provider,catalog,limiter);
        for(Integer steps:new Integer[]{null,0,-1,50001}) assertThrows(ApiException.class,
                ()->service.submit(new ZeppStepsRequest("test@example.com","password",steps,true)));
        assertThrows(ApiException.class,()->service.submit(new ZeppStepsRequest("test@example.com","password",10000,false)));
        verifyNoInteractions(provider,limiter);
    }
    @Test void limitsConcurrentSubmissionsAndReleasesSlotsAfterProviderFailure() throws Exception {
        var provider=mock(ZeppProvider.class);var catalog=mock(ToolUsageRepository.class);var limiter=mock(RateLimitService.class);
        when(catalog.visible("zepp_steps")).thenReturn(true);
        when(limiter.check(anyString(),eq(1),eq(60))).thenReturn(new RateLimitDecision(true,1,0,60));
        var entered=new CountDownLatch(4);var release=new CountDownLatch(1);
        when(provider.submit(anyString(),anyString(),anyInt())).thenAnswer(call->{
            entered.countDown();
            if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
            throw new ApiException(ApiError.ZEPP_PROVIDER_ERROR);
        });
        var service=new ZeppStepsService(ZeppHttpProviderTest.properties(),provider,catalog,limiter);
        try(var executor=Executors.newFixedThreadPool(4)) {
            var futures=new java.util.ArrayList<Future<?>>();
            try {
                for(int i=0;i<4;i++) futures.add(executor.submit(()->assertThrows(ApiException.class,()->service.submit(request()))));
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                assertEquals(ApiError.SERVICE_UNAVAILABLE,assertThrows(ApiException.class,()->service.submit(request())).error());
            } finally { release.countDown(); }
            for(var future:futures) future.get(5,TimeUnit.SECONDS);
        }
        assertEquals(ApiError.ZEPP_PROVIDER_ERROR,assertThrows(ApiException.class,()->service.submit(request())).error());
        verify(provider,times(5)).submit(anyString(),anyString(),anyInt());
    }
    private static ZeppStepsRequest request(){return new ZeppStepsRequest("test@example.com"," secret+& ",10000,true);}
}
