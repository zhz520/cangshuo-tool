package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaAlbumRegressionTest {
    private final MediaHttp http = mock(MediaHttp.class);
    private final MediaBrowser browser = mock(MediaBrowser.class);
    private final MediaJson json = new MediaJson();
    private final MediaParser parser = new MediaParser(http, json, browser);
    private static final String ID = "7589870649747125477";
    private static final String NOTE = "{\"aweme_id\":\"" + ID + "\",\"images\":[{\"url_list\":[\"https://p1.douyinpic.com/image.webp\",\"https://p2.douyinpic.com/image.jpg?sign=A%2FB\"],\"download_url_list\":[\"https://p1.douyinpic.com/watermark.jpg\"]}]}";
    private void pages(String text) {
        when(http.document(any(), any(), any(), anyBoolean(), anyLong())).thenAnswer(call -> new MediaHttp.Document(call.getArgument(1), text, List.of()));
        when(http.document(any(), any(), any(), anyString(), anyLong())).thenAnswer(call -> new MediaHttp.Document(call.getArgument(1), "{}", List.of()));
    }
    @Test void shortNoteConsumesAlreadyFetchedRouterWithoutContactingFeedOrSlides() {
        when(http.document(any(), any(), any(), anyBoolean(), anyLong())).thenAnswer(call ->
                new MediaHttp.Document(URI.create("https://www.iesdouyin.com/share/note/" + ID + "/"),
                        "_ROUTER_DATA={\"loaderData\":{\"note_(id)/page\":{\"videoInfoRes\":{\"item_list\":[" + NOTE + "]}}}};", List.of()));
        var result = parser.parse(MediaInput.parse("https://v.douyin.com/UhBKL-uRlgk/"));
        assertEquals("https://p2.douyinpic.com/image.jpg?sign=A%2FB", result.assets().get(0).uri().toString());
        assertEquals(List.of(URI.create("https://p1.douyinpic.com/image.webp")), result.assets().get(0).fallbacks());
        assertEquals(result.assets().get(0).fallbacks(), result.assets().get(1).fallbacks());
        verify(http, times(1)).document(any(), any(), any(), anyBoolean(), anyLong());
        verifyNoInteractions(browser);
    }
    @Test void timeoutOnOneRouteDoesNotPreventAnAlternatePublicSharePage() {
        pages("{}");
        when(http.document(any(), any(), any(), anyBoolean(), anyLong())).thenAnswer(call -> {
            URI uri = call.getArgument(1);
            if (uri.getPath().contains("/share/video/")) return new MediaHttp.Document(uri,
                    "_ROUTER_DATA={\"loaderData\":{\"video\":{\"videoInfoRes\":{\"item_list\":[" + NOTE + "]}}}};", List.of());
            throw new ApiException(ApiError.MEDIA_TIMEOUT);
        });
        assertEquals("IMAGE", parser.parse(MediaInput.parse("https://www.douyin.com/note/" + ID)).assets().get(0).kind());
    }
    @Test void anonymousReasonEightUsesBrowserAndNeverAcceptsAnotherRecommendedWork() {
        pages("{\"aweme_details\":[{\"aweme_id\":\"11111\",\"video\":{\"play_addr\":{\"url_list\":[\"https://v.douyinvod.com/other.mp4\"]}}}],\"filter_list\":[{\"reason\":8}]}");
        when(browser.enabled()).thenReturn(true);
        when(browser.douyin(eq(ID), anyLong())).thenReturn(json.json(NOTE));
        assertEquals("IMAGE", parser.parse(MediaInput.parse("https://www.douyin.com/note/" + ID)).assets().get(0).kind());
        verify(browser).douyin(eq(ID), anyLong());
    }
    @Test void missingBrowserKeepsFilterClassificationInsteadOfClaimingDeletion() {
        pages("{\"aweme_details\":null,\"filter_list\":[{\"reason\":8}]}");
        assertEquals(ApiError.MEDIA_PLATFORM_RESTRICTED, assertThrows(ApiException.class,
                () -> parser.parse(MediaInput.parse("https://www.douyin.com/note/" + ID))).error());
    }
    @Test void nestedImagePostKeepsAllFramesAndKnownLivePairs() {
        pages("{\"aweme_details\":[{\"image_post_info\":{\"images\":[{\"urlList\":[\"https://p.douyinpic.com/a.jpg\"],\"video\":{\"playAddr\":[{\"src\":\"https://v.douyinvod.com/playwm/live.mp4\"}]}},{\"urlList\":[\"https://p.douyinpic.com/b.jpg\"]}]}}]}");
        var result = parser.parse(MediaInput.parse("https://www.douyin.com/note/" + ID));
        assertEquals(List.of("IMAGE", "LIVE", "IMAGE", "COVER"), result.assets().stream().map(MediaParser.Candidate::kind).toList());
        assertTrue(result.assets().get(1).uri().getPath().contains("/play/"));
    }
    @Test void kuaishouLongVideoAcceptsActualKwimgsCdnAndManifestQualities() {
        pages("INIT_STATE={\"/long\":{\"result\":1,\"photo\":{\"caption\":\"long\",\"mainMvUrls\":[{\"url\":\"https://txmov2.a.kwimgs.com/video.mp4\"},{\"url\":\"https://tymov2.a.kwimgs.com/video.mp4\"}],\"manifest\":{\"adaptationSet\":[{\"representation\":[{\"width\":720,\"height\":1280,\"avgBitrate\":100,\"url\":\"https://txmov2.a.kwimgs.com/high.mp4\"}]}]},\"coverUrls\":[{\"url\":\"https://p.yximgs.com/cover.jpg\"}]}}};");
        var result = parser.parse(MediaInput.parse("https://v.m.chenzhongtech.com/fw/long-video/3xhjb2ywe3z7m6e"));
        assertEquals(100, result.assets().get(0).bitrate());
        assertEquals(List.of(URI.create("https://tymov2.a.kwimgs.com/video.mp4")), result.assets().get(1).fallbacks());
        assertEquals("COVER", result.assets().get(2).kind());
    }
    @Test void kuaishouSiblingAtlasPreservesCdnPathsWithoutDoubleScheme() {
        pages("INIT_STATE={\"share\":{\"result\":1,\"photo\":{\"caption\":\"atlas\"},\"atlas\":{\"cdnList\":[{\"cdn\":\"https://p.yximgs.com/\"}],\"list\":[\"/a.jpg\",\"https://p.yximgs.com/b.jpg\"]}}};");
        var result = parser.parse(MediaInput.parse("https://v.kuaishou.com/example"));
        assertEquals("https://p.yximgs.com/a.jpg", result.assets().get(0).uri().toString());
        assertEquals(2, result.assets().stream().filter(a -> a.kind().equals("IMAGE")).count());
    }
    @Test void kuaishouSerializedArrayAtlasAndApolloReferencesAreHandled() {
        pages("__APOLLO_STATE__={\"defaultClient\":{\"VisionVideoDetailPhoto:3xhjb2ywe3z7m6e\":{\"ext_params\":{\"atlas\":\"[{\\\"cdnUrls\\\":[{\\\"url\\\":\\\"https://p.yximgs.com/a.webp\\\"}]}]\"}}}};");
        assertEquals("IMAGE", parser.parse(MediaInput.parse("https://www.kuaishou.com/short-video/3xhjb2ywe3z7m6e")).assets().get(0).kind());
        pages("__APOLLO_STATE__={\"defaultClient\":{\"VisionVideoDetailPhoto:3xhjb2ywe3z7m6e\":{\"manifest\":{\"__ref\":\"manifest\"}},\"manifest\":{\"adaptationSet\":[{\"type\":\"id\",\"id\":\"set\"}]},\"set\":{\"representation\":[{\"__ref\":\"rep\"}]},\"rep\":{\"url\":{\"json\":[\"https://txmov2.a.kwimgs.com/play.mp4\"]}}}};");
        assertEquals("VIDEO", parser.parse(MediaInput.parse("https://www.kuaishou.com/short-video/3xhjb2ywe3z7m6e")).assets().get(0).kind());
    }
    @Test void kuaishouMissingH5FallsBackToPhotoRouteAndDoesNotExposeOpenProxy() {
        pages("no state");
        var counter = new AtomicInteger();
        when(http.document(any(), any(), any(), anyBoolean(), anyLong())).thenAnswer(call -> {
            URI uri = call.getArgument(1); counter.incrementAndGet();
            if (uri.getPath().contains("/fw/photo/")) return new MediaHttp.Document(uri,
                    "INIT_STATE={\"share\":{\"result\":1,\"photo\":{\"mainMvUrls\":[{\"url\":\"https://v.kwimgs.com/a.mp4\"}]}}};", List.of());
            return new MediaHttp.Document(URI.create("https://v.m.chenzhongtech.com/fw/long-video/3xhjb2ywe3z7m6e"), "empty", List.of());
        });
        assertEquals("VIDEO", parser.parse(MediaInput.parse("https://v.kuaishou.com/nTj2iJHt")).assets().get(0).kind());
        assertEquals(2, counter.get());
        assertThrows(ApiException.class, () -> MediaPlatform.KUAISHOU.mediaUri("https://kwimgs.com.evil.example/a.mp4"));
        var cycle = json.json("{\"a\":{\"__ref\":\"b\"},\"b\":{\"__ref\":\"a\"}}");
        assertThrows(ApiException.class, () -> KuaishouMetadata.resolve(cycle.path("a"), cycle));
    }
}
