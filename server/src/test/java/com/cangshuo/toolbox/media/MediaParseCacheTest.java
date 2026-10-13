package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import java.net.URI;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaParseCacheTest {
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-10-13T00:00:00Z");
    private final MediaParseCache cache = new MediaParseCache(clock);
    private final MediaParser.Parsed media = new MediaParser.Parsed(MediaPlatform.DOUYIN,
            URI.create("https://www.douyin.com/note/12345"), "", "", "", 0, List.of());
    @Test void successCacheReducesUpstreamRequestsAndRefreshesSignedUrlsAfterThreeMinutes() {
        when(clock.instant()).thenReturn(now);
        var calls = new AtomicInteger();
        var loader = (java.util.function.Supplier<MediaParser.Parsed>) () -> { calls.incrementAndGet(); return media; };
        assertSame(media, cache.load("same", loader)); assertSame(media, cache.load("same", loader));
        assertEquals(1, calls.get());
        when(clock.instant()).thenReturn(now.plusSeconds(180)); cache.load("same", loader);
        assertEquals(2, calls.get());
    }
    @Test void failedWorkCoolsDownForThirtySecondsButDoesNotDisableOtherWorks() {
        when(clock.instant()).thenReturn(now);
        var calls = new AtomicInteger();
        var loader = (java.util.function.Supplier<MediaParser.Parsed>) () -> { calls.incrementAndGet(); throw new ApiException(ApiError.MEDIA_PLATFORM_RESTRICTED); };
        for (int i = 0; i < 3; i++) assertEquals(ApiError.MEDIA_PLATFORM_RESTRICTED,
                assertThrows(ApiException.class, () -> cache.load("blocked", loader)).error());
        assertEquals(1, calls.get()); assertSame(media, cache.load("other", () -> media));
        when(clock.instant()).thenReturn(now.plusSeconds(30));
        assertThrows(ApiException.class, () -> cache.load("blocked", loader)); assertEquals(2, calls.get());
    }
    @Test void concurrentSameLinkSharesOneUpstreamCall() throws Exception {
        when(clock.instant()).thenReturn(now);
        var calls = new AtomicInteger(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> cache.load("same", () -> {
                calls.incrementAndGet(); entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("release timeout"); }
                catch (InterruptedException error) { throw new AssertionError(error); }
                return media;
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = pool.submit(() -> cache.load("same", () -> { calls.incrementAndGet(); return media; }));
            release.countDown();
            assertSame(media, first.get(5, TimeUnit.SECONDS)); assertSame(media, second.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        }
    }
    @Test void cacheIsBoundedAndServiceBusyIsNeverSticky() {
        when(clock.instant()).thenReturn(now);
        cache.load("first", () -> media);
        for (int i = 0; i < 100; i++) cache.load("item" + i, () -> media);
        var calls = new AtomicInteger(); cache.load("first", () -> { calls.incrementAndGet(); return media; });
        assertEquals(1, calls.get());
        assertThrows(ApiException.class, () -> cache.load("busy", () -> { throw new ApiException(ApiError.SERVICE_UNAVAILABLE); }));
        assertSame(media, cache.load("busy", () -> media));
    }
}
