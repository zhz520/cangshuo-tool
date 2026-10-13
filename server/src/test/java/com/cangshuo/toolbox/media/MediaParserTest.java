package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Synthetic public-page shapes, independent of third-party availability and real user media. */
class MediaParserTest {
    private final MediaHttp http = mock(MediaHttp.class);
    private final MediaParser parser = new MediaParser(http, new MediaJson());
    private void response(String json) { doAnswer(call -> new MediaHttp.Document(call.getArgument(1), json, List.of())).when(http).document(any(), any(), any(), anyBoolean(), anyLong()); }
    private MediaParser.Parsed parse(String url) { return parser.parse(MediaInput.parse(url)); }
    private static long count(MediaParser.Parsed result, String kind) { return result.assets().stream().filter(a -> a.kind().equals(kind)).count(); }
    @Test void douyinUsesSortedPlaybackVariantsWithoutDestroyingSignatures() {
        response("""
            {"aweme_details":[{"desc":"test","author":{"nickname":"author"},"video":{"duration":12500,
            "play_addr":{"url_list":["https://v.douyinvod.com/playwm/base/?sign=A%2FB"]},
            "bit_rate":[{"bit_rate":100,"play_addr":{"width":720,"height":1280,"url_list":["https://v.douyinvod.com/playwm/low/"]}},
                        {"bit_rate":200,"play_addr":{"width":1080,"height":1920,"url_list":["https://v.douyinvod.com/playwm/high/"]}}],
            "origin_cover":{"url_list":["https://p.douyinpic.com/cover.jpg"]}}}]}
            """);
        var result = parse("https://www.douyin.com/video/1234567890");
        assertEquals(12, result.durationSeconds()); assertEquals(3, count(result,"VIDEO")); assertEquals(1,count(result,"COVER"));
        assertEquals(200,result.assets().get(0).bitrate());
        assertEquals("https://v.douyinvod.com/play/base/?sign=A%2FB",result.assets().get(2).uri().toString());
    }
    @Test void douyinAlbumRetainsLivePairAndFailsRatherThanTruncatingMoreThanFiftyImages() {
        String image = "{\"url_list\":[\"https://p.douyinpic.com/1.jpg\"],\"video\":{\"play_addr\":{\"url_list\":[\"https://v.douyinvod.com/playwm/live/\"]}}}";
        response("{\"aweme_details\":[{\"images\":[" + image + "," + image + "]}]}");
        var result = parse("https://www.douyin.com/note/1234567890");
        assertEquals(List.of("IMAGE","LIVE","IMAGE","LIVE","COVER"),result.assets().stream().map(MediaParser.Candidate::kind).toList());
        response("{\"aweme_details\":[{\"images\":[" + String.join(",",java.util.Collections.nCopies(51,image)) + "]}]}");
        assertEquals(ApiError.MEDIA_TOO_LARGE,assertThrows(ApiException.class,() -> parse("https://www.douyin.com/note/1234567890")).error());
    }
    @Test void douyinFallsBackToRouterWhenApiIsUnavailable() {
        when(http.document(any(),any(),any(),anyBoolean(),anyLong())).thenAnswer(call -> {
            URI uri = call.getArgument(1); if (uri.getPath().contains("slidesinfo")) return new MediaHttp.Document(uri,"{}",List.of());
            return new MediaHttp.Document(uri,"<script>window._ROUTER_DATA={\"loaderData\":{\"video_(id)/page\":{\"videoInfoRes\":{\"item_list\":[{\"video\":{\"play_addr\":{\"url_list\":[\"https://v.douyinvod.com/play/a\"]}}}]}}}};</script>",List.of());
        });
        when(http.document(any(),any(),any(),anyString(),anyLong())).thenAnswer(call ->
                new MediaHttp.Document(call.getArgument(1), "{\"aweme_list\":[]}", List.of()));
        assertEquals(1,count(parse("https://www.douyin.com/video/1234567890"),"VIDEO"));
    }
    @Test void kuaishouSupportsBothVideoAndAtlas() {
        response("<script>window.INIT_STATE={\"/share\":{\"result\":1,\"photo\":{\"caption\":\"video\",\"duration\":2000,\"mainMvUrls\":[{\"url\":\"https://v.kwaicdn.com/video.mp4\"}],\"coverUrls\":[{\"url\":\"https://p.yximgs.com/cover.jpg\"}]}}};</script>");
        assertEquals(1,count(parse("https://v.kuaishou.com/a"),"VIDEO"));
        response("INIT_STATE={\"share\":{\"result\":1,\"photo\":{\"ext_params\":{\"atlas\":{\"cdn\":[\"p.yximgs.com\"],\"list\":[\"a.jpg\",\"b.jpg\"]}}}}};");
        assertEquals(2,count(parse("https://v.kuaishou.com/a"),"IMAGE"));
    }
    @Test void xhsExtractsOriginalImagesLivePhotoAndH264Only() {
        response("""
            <script>window.__INITIAL_STATE__={"other":undefined,"note":{"currentNoteId":"n1","noteDetailMap":{"n1":{"note":{"title":"album","user":{"nickname":"author"},"imageList":[
            {"width":100,"height":200,"urlDefault":"https://sns.xhscdn.com/notes_pre_post/spectrum/abcdefgh123!nd_dft_wlteh_webp_3","livePhoto":true,"stream":{"h264":[{"masterUrl":"https://sns-video.xhscdn.com/live.mp4"}]}},
            {"urlDefault":"https://sns.xhscdn.com/other.jpg"}]}}}}};</script>
            """);
        var result = parse("https://www.xiaohongshu.com/explore/abcdef?xsec_token=keep");
        assertEquals("https://ci.xiaohongshu.com/notes_pre_post/spectrum/abcdefgh123?imageView2/format/jpg",result.assets().get(0).uri().toString());
        assertEquals(List.of("IMAGE","LIVE","IMAGE","COVER"),result.assets().stream().map(MediaParser.Candidate::kind).toList());
        assertEquals("UNKNOWN",result.assets().get(2).source());
        response("__INITIAL_STATE__={\"note\":{\"currentNoteId\":\"n\",\"noteDetailMap\":{\"n\":{\"note\":{\"video\":{\"media\":{\"stream\":{\"h264\":[{\"masterUrl\":\"https://v.xhscdn.com/video.mp4\"}]}}},\"imageList\":[]}}}}}");
        assertEquals(1,count(parse("https://xhslink.com/a"),"VIDEO"));
    }
    @Test void bilibiliHonorsPageAndActualQualityAndRejectsSegmentedStreams() {
        when(http.document(any(),any(),any(),anyBoolean(),anyLong())).thenAnswer(call -> {
            URI uri = call.getArgument(1);
            String body = uri.getPath().endsWith("/view") ? "{\"code\":0,\"data\":{\"title\":\"title\",\"pages\":[{\"cid\":1},{\"cid\":2,\"duration\":20}],\"pic\":\"https://i.hdslb.com/cover.jpg\",\"owner\":{\"name\":\"author\"}}}" : "{\"code\":0,\"data\":{\"quality\":32,\"accept_quality\":[80,32],\"accept_description\":[\"1080p\",\"480p\"],\"durl\":[{\"url\":\"https://v.bilivideo.com/video.mp4\"}]}}";
            if (uri.getPath().endsWith("playurl")) assertTrue(uri.getQuery().contains("cid=2"));
            return new MediaHttp.Document(uri,body,List.of());
        });
        var result = parse("https://www.bilibili.com/video/BV1xx411c7mD/?p=2");
        assertEquals("480p",result.assets().get(0).label()); assertEquals("title · P2",result.title());
        response("{\"code\":0,\"data\":{\"pages\":[{\"cid\":1}],\"durl\":[{\"url\":\"a\"},{\"url\":\"b\"}]}}");
        assertEquals(ApiError.MEDIA_FORMAT_UNSUPPORTED,assertThrows(ApiException.class,() -> parse("https://www.bilibili.com/video/BV1xx411c7mD")).error());
    }
    @Test void weiboExtractsVideoCoverAndPostAlbum() {
        response("{\"data\":{\"Component_Play_Playinfo\":{\"title\":\"title\",\"urls\":{\"720p\":\"//f.video.weibocdn.com/video.mp4\"},\"cover_image\":\"https://wx1.sinaimg.cn/cover.jpg\"}}}");
        assertEquals(1,count(parse("https://weibo.com/tv/show/1034:123456"),"VIDEO"));
        response("{\"data\":{\"text\":\"<b>title</b>\",\"pics\":[{\"large\":{\"url\":\"https://wx1.sinaimg.cn/1.jpg\"}},{\"url\":\"https://wx1.sinaimg.cn/2.jpg\"}]}}");
        var result = parse("https://m.weibo.cn/detail/1234567890"); assertEquals(2,count(result,"IMAGE")); assertEquals("title",result.title());
    }
    @Test void xiguaAndPipixiaUseTheirOwnPublicStructures() {
        response("_ROUTER_DATA={\"loaderData\":{\"video\":{\"videoInfoRes\":{\"item_list\":[{\"video\":{\"play_addr\":{\"url_list\":[\"https://v.ixiguavideo.com/video.mp4\"]}}}]}}}}");
        assertEquals(1,count(parse("https://www.ixigua.com/1234567890"),"VIDEO"));
        response("{\"status_code\":0,\"data\":{\"cell_comments\":[{\"comment_info\":{\"item\":{\"video\":{\"video_high\":{\"url_list\":[\"https://v.snssdk.com/video.mp4\"]}},\"note\":{\"multi_image\":[{\"url_list\":[\"https://p.pstatp.com/1.jpg\"]}]}}}}]}}");
        var result = parse("https://h5.pipix.com/item/1234567890"); assertEquals(1,count(result,"VIDEO")); assertEquals(1,count(result,"IMAGE"));
        assertEquals("UNKNOWN",result.assets().stream().filter(a -> a.kind().equals("VIDEO")).findFirst().orElseThrow().source());
    }
    @Test void tiktokUsesRehydrationAndSigiFallbackAndSupportsPhotoPosts() {
        response("<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"itemInfo\":{\"itemStruct\":{\"desc\":\"video\",\"video\":{\"duration\":5,\"playAddr\":\"https://v.tiktokcdn.com/play.mp4\",\"originCover\":\"https://p.tiktokcdn.com/cover.jpg\"}}}}}}</script>");
        assertEquals(1,count(parse("https://www.tiktok.com/@test/video/1234567890"),"VIDEO"));
        response("<script id='SIGI_STATE'>{\"ItemModule\":{\"1234567890\":{\"imagePost\":{\"images\":[{\"imageURL\":{\"urlList\":[\"https://p.tiktokcdn.com/1.jpg\"]}}]}}}}</script>");
        assertEquals(1,count(parse("https://www.tiktok.com/@test/photo/1234567890"),"IMAGE"));
    }
    @Test void neverReturnsCoverOnlyOrOffPlatformMediaAsSuccess() {
        response("{\"aweme_details\":[{\"video\":{\"cover\":{\"url_list\":[\"https://p.douyinpic.com/cover.jpg\"]}}}]}");
        assertThrows(ApiException.class,() -> parse("https://www.douyin.com/video/1234567890"));
        response("{\"aweme_details\":[{\"video\":{\"play_addr\":{\"url_list\":[\"https://evil.example/video.mp4\"]}}}]}");
        assertEquals(ApiError.MEDIA_PROVIDER_ERROR,assertThrows(ApiException.class,() -> parse("https://www.douyin.com/video/1234567890")).error());
    }
    @Test void xiaohongshuReadsMobileH5StateAndBuildsWatermarkFreeSources() {
        when(http.document(any(),any(),any(),anyBoolean(),anyLong())).thenAnswer(call -> {
            URI uri = call.getArgument(1);
            if (Boolean.TRUE.equals(call.getArgument(3)) || uri.getPath().contains("/404"))
                return new MediaHttp.Document(uri,"<script>window.__INITIAL_STATE__={\"note\":{\"currentNoteId\":\"undefined\",\"noteDetailMap\":{}}}</script>",List.of());
            return new MediaHttp.Document(uri,"<script>window.__INITIAL_STATE__={\"noteData\":{\"data\":{\"noteData\":{\"title\":\"mobile\",\"desc\":\"desc\",\"user\":{\"nickName\":\"m-user\"},\"imageList\":[{\"fileId\":\"abcdefgh1234567890\",\"url\":\"https://sns-webpic-qc.xhscdn.com/2024/img!nd_dft_wlteh_webp_3\",\"width\":1080,\"height\":1440,\"livePhoto\":true,\"stream\":{\"h264\":[{\"masterUrl\":\"https://sns-video-bd.xhscdn.com/live.mp4\"}]}}]}}}}</script>",List.of());
        });
        var result = parse("https://www.xiaohongshu.com/explore/674051740000000007027a15?xsec_token=abc");
        assertEquals("mobile",result.title()); assertEquals("m-user",result.author());
        assertEquals("https://sns-img-qc.xhscdn.com/abcdefgh1234567890?imageView2/2/w/1920/format/jpg",result.assets().get(0).uri().toString());
        assertEquals(List.of("IMAGE","LIVE","COVER"),result.assets().stream().map(MediaParser.Candidate::kind).toList());
    }
    @Test void xiaohongshuDistinguishesExpiredSharesFromUnavailableNotes() {
        when(http.document(any(),any(),any(),anyBoolean(),anyLong())).thenAnswer(call -> {
            URI uri = (URI) call.getArgument(1);
            if (uri.toString().contains("expired"))
                return new MediaHttp.Document(URI.create("https://www.xiaohongshu.com/404?error_code=300031&error_msg=x"),"<html>404</html>",List.of());
            return new MediaHttp.Document(uri,"<html>no state</html>",List.of());
        });
        assertEquals(ApiError.MEDIA_PLATFORM_RESTRICTED,assertThrows(ApiException.class,
                () -> parse("https://www.xiaohongshu.com/explore/expired674051740000000007027a15")).error());
        assertEquals(ApiError.MEDIA_UNAVAILABLE,assertThrows(ApiException.class,
                () -> parse("https://www.xiaohongshu.com/discovery/item/674051740000000007027a15")).error());
    }
    @Test void douyinNotesUseMobileFeedThenTheNoteSharePage() {
        when(http.document(any(),any(),any(),anyString(),anyLong())).thenAnswer(call -> {
            URI uri = call.getArgument(1);
            return new MediaHttp.Document(uri,"{\"aweme_list\":[{\"aweme_id\":\"111\"}]}",List.of());
        });
        response("{\"aweme_details\":[]}");
        when(http.document(any(),any(),any(),anyBoolean(),anyLong())).thenAnswer(call -> {
            URI uri = call.getArgument(1);
            if (uri.getPath().contains("/share/note/"))
                return new MediaHttp.Document(uri,"<script>window._ROUTER_DATA={\"loaderData\":{\"note_(id)/page\":{\"videoInfoRes\":{\"item_list\":[{\"desc\":\"note\",\"images\":[{\"url_list\":[\"https://p.douyinpic.com/1.jpg\"]}]}]}}}};</script>",List.of());
            return new MediaHttp.Document(uri,"<html></html>",List.of());
        });
        var result = parse("https://www.douyin.com/note/7285559250619813155");
        assertEquals(List.of("IMAGE","COVER"),result.assets().stream().map(MediaParser.Candidate::kind).toList());
    }
    @Test void noteAndImageSourceHelpersCoverBothPlatformShapes() {
        assertEquals("674051740000000007027a15",MediaParser.noteId(URI.create("https://www.xiaohongshu.com/discovery/item/674051740000000007027a15?xsec_token=x")));
        assertEquals("",MediaParser.noteId(URI.create("https://xhslink.com/a/abcdef")));
        assertEquals("https://sns-img-qc.xhscdn.com/abcdefgh1234567890?imageView2/2/w/1920/format/jpg",
                MediaParser.xhsImage("https://sns-webpic-qc.xhscdn.com/x!webp","abcdefgh1234567890"));
        assertEquals("https://ci.xiaohongshu.com/notes_pre_post/spectrum/abcdefgh123?imageView2/format/jpg",
                MediaParser.xhsImage("https://sns-webpic-qc.xhscdn.com/notes_pre_post/spectrum/abcdefgh123!webp",""));
        assertEquals("https://sns-webpic-qc.xhscdn.com/a/b.jpg?imageView2/2/w/1080",
                MediaParser.xhsImage("https://sns-webpic-qc.xhscdn.com/a/b.jpg?imageView2/2/w/1080",""));
        assertEquals("",MediaParser.xhsImage("",""));
        assertEquals("https://www.xiaohongshu.com/explore/674051740000000007027a15?source=webshare",
                MediaParser.withoutToken(URI.create("https://www.xiaohongshu.com/explore/674051740000000007027a15?xsec_token=abc&xsec_source=pc_share&source=webshare")).toString());
        assertNull(MediaParser.withoutToken(URI.create("https://www.xiaohongshu.com/explore/674051740000000007027a15?source=webshare")));
        assertNull(MediaParser.withoutToken(URI.create("https://www.xiaohongshu.com/explore/674051740000000007027a15")));
        assertTrue(MediaParser.isNote(URI.create("https://www.douyin.com/note/1")));
        assertTrue(MediaParser.isNote(URI.create("https://www.iesdouyin.com/share/note/1/")));
        assertFalse(MediaParser.isNote(URI.create("https://www.douyin.com/video/1")));
    }
}
