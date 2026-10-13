package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.ratelimit.*;
import com.cangshuo.toolbox.tool.repository.ToolUsageRepository;
import java.io.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipInputStream;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaServiceTest {
    private final MediaParser parser = mock(MediaParser.class);
    private final MediaHttp http = mock(MediaHttp.class);
    private final ToolUsageRepository catalog = mock(ToolUsageRepository.class);
    private final RateLimitService limiter = mock(RateLimitService.class);
    private final Clock clock = mock(Clock.class);
    private MediaService service;
    private final Instant now = Instant.parse("2026-10-13T01:00:00Z");
    private MediaParser.Parsed fixture;
    @BeforeEach void setup() {
        when(catalog.visible(MediaService.CODE)).thenReturn(true);
        when(limiter.check(anyString(),anyInt(),anyInt())).thenReturn(new RateLimitDecision(true,12,11,60));
        when(clock.instant()).thenReturn(now);
        fixture = new MediaParser.Parsed(MediaPlatform.DOUYIN,URI.create("https://www.douyin.com/note/1234567890"),"test","description","author",0,List.of(
                candidate("IMAGE","a.jpg"),candidate("LIVE","live.mp4"),candidate("IMAGE","b.jpg"),candidate("COVER","cover.jpg")));
        when(parser.parse(any())).thenReturn(fixture);
        service = new MediaService(parser,http,catalog,limiter,true,clock);
    }
    private static MediaParser.Candidate candidate(String kind, String file) { return new MediaParser.Candidate(kind,"",URI.create("https://p.douyinpic.com/" + file),100,200,0,"PLATFORM_SOURCE"); }
    private MediaService.Result result() { return service.parse("https://www.douyin.com/note/1234567890","client"); }
    private static void rejects(ApiError expected, Runnable action) { assertEquals(expected,assertThrows(ApiException.class,action::run).error()); }
    private static MediaHttp.Download download(byte[] bytes, String mime, long length) { return new MediaHttp.Download(mock(ClassicHttpResponse.class),new ByteArrayInputStream(bytes),mime,length,200,null,MediaHttp.deadline(10)); }
    @Test void parseReturnsOnlyOpaqueSameOriginHandlesAndFixedExpiry() {
        var result = result(); assertTrue(result.id().matches("[a-f0-9]{32}")); assertEquals(now.plusSeconds(900),result.expiresAt());
        for (var asset : result.assets()) assertEquals("/api/v1/web-tools/short_video_parse/assets/" + result.id() + "/" + asset.index() + "?download=true",asset.downloadPath());
        assertEquals(8,service.capabilities().platforms().size());
        verify(limiter).check("toolbox:rate:media:parse:client",12,60);
    }
    @Test void catalogueMaintenanceAndGlobalSwitchStopBothParseAndTransfers() {
        String id = result().id(); when(catalog.visible(MediaService.CODE)).thenReturn(false);
        assertFalse(service.capabilities().available()); rejects(ApiError.SERVICE_UNAVAILABLE,() -> result());
        rejects(ApiError.SERVICE_UNAVAILABLE,() -> service.transfer(id,0,null));
        rejects(ApiError.SERVICE_UNAVAILABLE,() -> service.album(id,"0","client"));
        when(catalog.visible(MediaService.CODE)).thenReturn(true);
        var disabled = new MediaService(parser,http,catalog,limiter,false,clock);
        assertFalse(disabled.capabilities().available()); rejects(ApiError.SERVICE_UNAVAILABLE,() -> disabled.parse("https://v.douyin.com/a","client"));
        verifyNoInteractions(http);
    }
    @Test void rateLimitedOrMalformedParseNeverContactsPlatforms() {
        rejects(ApiError.INVALID_ARGUMENT,() -> service.parse("bad","client"));
        when(limiter.check(anyString(),anyInt(),anyInt())).thenReturn(new RateLimitDecision(false,12,0,60));
        rejects(ApiError.TOO_MANY_REQUESTS,() -> result()); verifyNoInteractions(parser,http);
    }
    @Test void tokensExpireAreBoundedAndCanBeExplicitlyDeleted() {
        String first = result().id(); for (int i = 0; i < 100; i++) result();
        rejects(ApiError.MEDIA_RESULT_EXPIRED,() -> service.transfer(first,0,null));
        String last = result().id(); service.clear(last); rejects(ApiError.MEDIA_RESULT_EXPIRED,() -> service.transfer(last,0,null));
        String expiring = result().id(); when(clock.instant()).thenReturn(now.plusSeconds(900));
        rejects(ApiError.MEDIA_RESULT_EXPIRED,() -> service.transfer(expiring,0,null));
        rejects(ApiError.INVALID_ARGUMENT,() -> service.transfer("http://localhost",0,null));
        verifyNoInteractions(http);
    }
    @Test void rangeAndAssetSelectionAreCheckedBeforeOpeningMedia() {
        String id = result().id();
        for (String range : List.of("bytes=-100", "bytes=0-1,2-3", "bytes=2-1", "bytes=209715200-", "bytes=0-209715200", "bytes=0-\r\nHost:localhost"))
            rejects(ApiError.INVALID_ARGUMENT,() -> service.transfer(id,0,range));
        rejects(ApiError.INVALID_ARGUMENT,() -> service.transfer(id,-1,null)); rejects(ApiError.INVALID_ARGUMENT,() -> service.transfer(id,4,null));
        assertDoesNotThrow(() -> MediaService.validateRange("bytes=0-")); assertDoesNotThrow(() -> MediaService.validateRange("bytes=1-100"));
        for (String selection : List.of("", "0,0", "-1", "1", "3", "4", "0%2C2", "0,2,"))
            rejects(ApiError.INVALID_ARGUMENT,() -> service.album(id,selection,"client"));
        assertEquals(List.of(0,2),MediaService.selections("2,0",fixture)); verifyNoInteractions(http);
    }
    @Test void completeZipUsesSourceOrderAndAuthenticFileTypes() throws Exception {
        byte[] first = {1,2,3}, second = {4,5};
        when(http.open(any(),eq(fixture.assets().get(0).uri()),isNull(),anyLong())).thenReturn(download(first,"image/jpeg",3));
        when(http.open(any(),eq(fixture.assets().get(2).uri()),isNull(),anyLong())).thenReturn(download(second,"image/png",2));
        try (var archive = service.album(result().id(),"2,0","client"); var zip = new ZipInputStream(new ByteArrayInputStream(archive.bytes()))) {
            assertEquals("douyin_album.zip",archive.fileName()); assertEquals("001.jpg",zip.getNextEntry().getName()); assertArrayEquals(first,zip.readAllBytes());
            assertEquals("002.png",zip.getNextEntry().getName()); assertArrayEquals(second,zip.readAllBytes()); assertNull(zip.getNextEntry());
        }
    }
    @Test void failedAndOversizedArchivesDoNotReturnPartialZipAndReleaseTheirSlot() {
        String id = result().id();
        when(http.open(any(),any(),any(),anyLong())).thenAnswer(call -> download(new byte[0],"image/jpeg",MediaService.MAX_ZIP_BYTES + 1L));
        rejects(ApiError.MEDIA_TOO_LARGE,() -> service.album(id,"0","client"));
        when(http.open(any(),any(),any(),anyLong())).thenAnswer(call -> download(new byte[]{1},"image/jpeg",2));
        rejects(ApiError.MEDIA_PROVIDER_ERROR,() -> service.album(id,"0","client"));
        when(http.open(any(),any(),any(),anyLong())).thenAnswer(call -> download(new byte[]{1},"video/mp4",1));
        rejects(ApiError.MEDIA_FORMAT_UNSUPPORTED,() -> service.album(id,"0","client"));
        when(http.open(any(),any(),any(),anyLong())).thenAnswer(call -> download(new byte[]{1},"image/jpeg",1));
        assertDoesNotThrow(() -> { try (var ignored = service.album(id,"0","client")) {} });
    }
    @Test void transferSlotsAreReleasedAfterFailureAndCloseIsIdempotent() throws Exception {
        String id = result().id(); when(http.open(any(),any(),any(),anyLong())).thenThrow(new IllegalStateException("must never leak"));
        for (int i = 0; i < 10; i++) rejects(ApiError.MEDIA_PROVIDER_ERROR,() -> service.transfer(id,0,null));
        reset(http);
        when(http.open(any(),any(),any(),anyLong())).thenAnswer(call -> download(new byte[]{1,2},"image/jpeg",2));
        var prepared = service.transfer(id,0,"bytes=0-1"); prepared.close(); prepared.close();
        assertEquals(8,prepared.slots().availablePermits());
        var streams = new ArrayList<MediaService.PreparedTransfer>();
        try { for (int i = 0; i < 8; i++) streams.add(service.transfer(id,0,null)); rejects(ApiError.SERVICE_UNAVAILABLE,() -> service.transfer(id,0,null)); }
        finally { for (var stream : streams) stream.close(); }
        var slots = new Semaphore(0); var archive = new MediaService.PreparedArchive(new byte[]{1},"a.zip",slots); archive.close(); archive.close(); assertEquals(1,slots.availablePermits());
    }
    @Test void streamBoundsAndPrematureEofAreNeverReportedAsComplete() throws Exception {
        var out = new ByteArrayOutputStream();
        assertThrows(IOException.class,() -> download(new byte[]{1},"image/jpeg",2).writeTo(out,10));
        rejects(ApiError.MEDIA_TOO_LARGE,() -> { try { download(new byte[]{1,2,3},"image/jpeg",3).writeTo(new ByteArrayOutputStream(),2); } catch (IOException error) { throw new RuntimeException(error); } });
        rejects(ApiError.MEDIA_TOO_LARGE,() -> { try { MediaHttp.readLimited(new ByteArrayInputStream(new byte[3]),2,MediaHttp.deadline(1)); } catch (IOException error) { throw new RuntimeException(error); } });
        rejects(ApiError.MEDIA_TIMEOUT,() -> MediaHttp.checkDeadline(System.nanoTime() - 1));
    }
}
