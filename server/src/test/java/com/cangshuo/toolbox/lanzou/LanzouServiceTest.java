package com.cangshuo.toolbox.lanzou;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.ratelimit.*;
import com.cangshuo.toolbox.tool.repository.ToolUsageRepository;
import java.net.URI;
import java.time.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LanzouServiceTest {
    private final LanzouParser parser = mock(LanzouParser.class);
    private final ToolUsageRepository catalog = mock(ToolUsageRepository.class);
    private final RateLimitService limiter = mock(RateLimitService.class);
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-10-22T01:00:00Z");
    private LanzouService service;
    private final URI share = URI.create("https://www.lanzouf.com/iAbC123");

    @BeforeEach void setup() {
        when(catalog.visible(LanzouService.CODE)).thenReturn(true);
        when(limiter.check(anyString(), eq(8), eq(60))).thenReturn(new RateLimitDecision(true, 8, 7, 60));
        when(limiter.check(anyString(), eq(30), eq(60))).thenReturn(new RateLimitDecision(true, 30, 29, 60));
        when(clock.instant()).thenReturn(now);
        when(parser.parse(any(), anyLong())).thenReturn(new LanzouParser.Parsed(LanzouParser.State.DIRECT, share,
                "a.zip", "1 MB", URI.create("https://download.lanzouf.com/file/x"),
                URI.create("https://cdn.example.com/a.zip?fn=a.zip"), false, false));
        service = new LanzouService(parser, catalog, limiter, true, clock);
    }
    private static void rejects(ApiError expected, Runnable action) {
        assertEquals(expected, assertThrows(ApiException.class, action::run).error());
    }
    @Test void successfulResultIsBoundedAndSharedFromCacheByLinkAndPasswordHash() {
        var first = service.parse("https://lanzouf.com/iAbC123", "", "client");
        var second = service.parse("https://lanzouf.com/iAbC123", "", "client");
        assertFalse(first.cached()); assertTrue(second.cached()); assertEquals(first.directUrl(), second.directUrl());
        assertEquals(now, first.resolvedAt()); assertEquals(180, first.expiresInSeconds());
        verify(parser, times(1)).parse(any(), anyLong());
        service.parse("https://lanzouf.com/iAbC123", "other", "client");
        verify(parser, times(2)).parse(any(), anyLong());
        assertEquals(2, service.capabilities().maxConcurrent());
        assertEquals("OFFICIAL_PAGE_FALLBACK", service.capabilities().verificationMode());
        assertFalse(service.capabilities().permanentLinksAvailable());
    }
    @Test void maintenanceSwitchAndRateLimitStopBeforeUpstream() {
        var disabled = new LanzouService(parser, catalog, limiter, false, clock);
        rejects(ApiError.SERVICE_UNAVAILABLE, () -> disabled.parse("https://lanzouf.com/i1", "", "client"));
        when(catalog.visible(LanzouService.CODE)).thenReturn(false);
        assertFalse(service.capabilities().available());
        rejects(ApiError.SERVICE_UNAVAILABLE, () -> service.parse("https://lanzouf.com/i1", "", "client"));
        when(catalog.visible(LanzouService.CODE)).thenReturn(true);
        when(limiter.check(anyString(), anyInt(), anyInt())).thenReturn(new RateLimitDecision(false, 8, 0, 60));
        rejects(ApiError.TOO_MANY_REQUESTS, () -> service.parse("https://lanzouf.com/i1", "", "client"));
        verifyNoInteractions(parser);
    }
    @Test void upstreamRestrictionCreatesShortHostCooldownAndDoesNotCacheFailure() {
        when(parser.parse(any(), anyLong())).thenThrow(new ApiException(ApiError.LANZOU_RESTRICTED));
        rejects(ApiError.LANZOU_RESTRICTED, () -> service.parse("https://lanzouf.com/i1", "", "client"));
        rejects(ApiError.LANZOU_RESTRICTED, () -> service.parse("https://lanzouf.com/i2", "", "client"));
        verify(parser, times(1)).parse(any(), anyLong());
        when(clock.instant()).thenReturn(now.plusSeconds(61));
        rejects(ApiError.LANZOU_RESTRICTED, () -> service.parse("https://lanzouf.com/i2", "", "client"));
        verify(parser, times(2)).parse(any(), anyLong());
    }
    @Test void officialFallbackIsSuccessfulAndExplicit() {
        when(parser.parse(any(), anyLong())).thenReturn(new LanzouParser.Parsed(LanzouParser.State.OFFICIAL_PAGE, share,
                "b.txt", "2 KB", URI.create("https://download.lanzouf.com/file/y"), null, true, false));
        var result = service.parse("https://lanzouf.com/iAbC123", "", "client");
        assertEquals("OFFICIAL_PAGE", result.state()); assertNull(result.directUrl()); assertTrue(result.verificationRequired());
        assertEquals("https://download.lanzouf.com/file/y", result.downloadPageUrl());
    }
    @Test void unresolvedPageVerificationReturnsTheOfficialShareInsteadOfFakeData() {
        when(parser.parse(any(), anyLong())).thenThrow(new ApiException(ApiError.LANZOU_VERIFICATION_REQUIRED));
        var result = service.parse("https://www.lanzouf.com/iAbC123", "1234", "client");
        assertEquals("OFFICIAL_PAGE", result.state()); assertTrue(result.verificationRequired());
        assertNull(result.directUrl()); assertEquals(share.toString(), result.downloadPageUrl());
        assertEquals("", result.fileName());
    }
    @Test void permanentLinkStoresOnlyEncryptedCapabilityAndRedirectsThroughFreshResult() {
        var permanentService = new LanzouService(parser, catalog, limiter, true, clock,
                new LanzouLinkTokenCodec("p".repeat(32)));
        assertTrue(permanentService.capabilities().permanentLinksAvailable());
        var created = permanentService.permanentLink("https://lanzouf.com/iAbC123", "secret", "client");
        assertTrue(created.path().startsWith(LanzouService.DOWNLOAD_PATH));
        assertFalse(created.path().contains("secret")); assertFalse(created.path().contains("lanzouf"));
        assertEquals("DIRECT", created.state());
        var token = created.path().substring(LanzouService.DOWNLOAD_PATH.length());
        var redirected = permanentService.permanentRedirect(token, "visitor");
        assertEquals("https://cdn.example.com/a.zip?fn=a.zip", redirected.directUrl());
        verify(parser, times(1)).parse(any(), anyLong());
        rejects(ApiError.LANZOU_LINK_INVALID, () -> permanentService.permanentRedirect(token + "x", "visitor"));
    }
    @Test void permanentLinkFailsClosedWithoutSecretAndHonorsDownloadBudget() {
        rejects(ApiError.SERVICE_UNAVAILABLE, () -> service.permanentLink("https://lanzouf.com/i1", "", "client"));
        var permanentService = new LanzouService(parser, catalog, limiter, true, clock,
                new LanzouLinkTokenCodec("q".repeat(32)));
        var created = permanentService.permanentLink("https://lanzouf.com/iAbC123", "", "client");
        var token = created.path().substring(LanzouService.DOWNLOAD_PATH.length());
        when(limiter.check(startsWith("toolbox:rate:lanzou:download:"), eq(30), eq(60)))
                .thenReturn(new RateLimitDecision(false, 30, 0, 60));
        rejects(ApiError.TOO_MANY_REQUESTS, () -> permanentService.permanentRedirect(token, "visitor"));
    }
}
