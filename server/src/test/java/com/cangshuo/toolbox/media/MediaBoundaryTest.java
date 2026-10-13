package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import java.net.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaBoundaryTest {
    private static void rejects(ApiError expected, Runnable action) { assertEquals(expected, assertThrows(ApiException.class, action::run).error()); }
    @Test void acceptsFullShareTextAndPreservesSignedQuery() {
        var value = MediaInput.parse("复制此文案： http://v.douyin.com/abc/?x=a%2Fb&signature=A%2BB#share 。");
        assertEquals(MediaPlatform.DOUYIN, value.platform());
        assertEquals("https://v.douyin.com/abc/?x=a%2Fb&signature=A%2BB", value.uri().toString());
        assertEquals(MediaPlatform.XIAOHONGSHU, MediaInput.parse("笔记 https://xhslink.com/abc，").platform());
    }
    @Test void rejectsAmbiguousInputCredentialsSpoofedHostsPortsAndControlText() {
        for (String text : new String[]{"", "x".repeat(4097), "https://v.douyin.com/a\u0000", "https://v.douyin.com/a https://b23.tv/b", "https://u:p@v.douyin.com/a", "https://v.douyin.com:8888/a", "javascript:alert(1)", "https://v.douyin.com./a"})
            rejects(ApiError.INVALID_ARGUMENT, () -> MediaInput.parse(text));
        for (String text : new String[]{"https://v.douyin.com.evil.example/a", "https://127.0.0.1/a", "https://localhost/a", "https://example.com/?url=https%3A%2F%2Fv.douyin.com"})
            rejects(ApiError.MEDIA_UNSUPPORTED_PLATFORM, () -> MediaInput.parse(text));
    }
    @Test void platformRedirectsAndMediaCannotBecomeAnOpenProxy() {
        MediaPlatform.DOUYIN.validatePage(URI.create("https://www.iesdouyin.com/share/video/12345"));
        assertEquals("https://v.example.douyinvod.com/play/x?signature=A%2FB", MediaPlatform.DOUYIN.mediaUri("//v.example.douyinvod.com/play/x?signature=A%2FB").toString());
        for (String uri : new String[]{"https://douyinvod.com.evil.example/v.mp4", "https://127.0.0.1/v.mp4", "https://evil.example/v.mp4", "file:///data", "https://u:p@v.douyinvod.com/v.mp4", "https://v.douyinvod.com:8080/v.mp4"})
            rejects(ApiError.MEDIA_PROVIDER_ERROR, () -> MediaPlatform.DOUYIN.mediaUri(uri));
        rejects(ApiError.MEDIA_PROVIDER_ERROR, () -> MediaPlatform.DOUYIN.validatePage(URI.create("https://www.bilibili.com/video/BV1")));
        rejects(ApiError.MEDIA_FORMAT_UNSUPPORTED, () -> MediaPlatform.DOUYIN.mediaUri("https://v.douyinvod.com/live.m3u8?token=x"));
    }
    @Test void dnsBlocksPrivateReservedAndTransitionAddressesUsedByTheConnection() throws Exception {
        for (String ip : new String[]{"0.0.0.0", "10.0.0.1", "127.0.0.1", "169.254.169.254", "172.16.0.1", "192.168.0.1", "100.64.0.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "255.255.255.255", "::1", "::", "fe80::1", "fc00::1", "2001:db8::1", "2002:7f00:1::1", "::ffff:127.0.0.1"})
            assertFalse(PublicMediaDns.isPublic(InetAddress.getByName(ip)), ip);
        for (String ip : new String[]{"1.1.1.1", "8.8.8.8", "172.32.1.1", "100.128.1.1", "2606:4700:4700::1111"})
            assertTrue(PublicMediaDns.isPublic(InetAddress.getByName(ip)), ip);
        assertThrows(UnknownHostException.class, () -> new PublicMediaDns().resolve("127.0.0.1"));
    }
    @Test void serializedStateIsJsonNotExecutableJavascriptAndDoesNotRewriteStrings() {
        var json = new MediaJson();
        var state = json.assignment("<script>window.__INITIAL_STATE__={\"missing\":undefined,\"text\":\"undefined } \\\" quoted\",\"rows\":[undefined,1]}; alert(1)</script>", "__INITIAL_STATE__");
        assertTrue(state.path("missing").isNull()); assertTrue(state.path("rows").path(0).isNull());
        assertEquals("undefined } \" quoted", state.path("text").asText());
        assertEquals(1, json.script("<script type='application/json' id='DATA'>{\"n\":1}</script>", "DATA").path("n").asInt());
        for (String input : new String[]{"null", "{\"n\":1} {\"n\":2}", "{\"n\":NaN}", "[".repeat(65) + "0" + "]".repeat(65)})
            rejects(ApiError.MEDIA_UNAVAILABLE, () -> json.json(input));
        for (String html : new String[]{"DATA=alert(1)", "DATA={\"a\":undefinedFunction()}", "DATA={\"a\":1", "DATA={a:1}"})
            rejects(ApiError.MEDIA_UNAVAILABLE, () -> json.assignment(html, "DATA"));
    }
    @Test void operatorCookieIsScopedToXiaohongshuAndRejectsMalformedValues() {
        assertEquals("a1=abc; webId=def", MediaHttp.platformCookie(MediaPlatform.XIAOHONGSHU, " a1=abc; webId=def "));
        for (String value : new String[]{"", "   ", "short", "a1=abc\r\nHost: evil", "a1=abc\u0000", "a".repeat(1025)})
            assertEquals("", MediaHttp.platformCookie(MediaPlatform.XIAOHONGSHU, value), value);
        assertEquals("", MediaHttp.platformCookie(MediaPlatform.DOUYIN, "a1=abc; webId=def"));
        assertEquals("", MediaHttp.platformCookie(MediaPlatform.XIAOHONGSHU, null));
    }
}
