package com.cangshuo.toolbox.lanzou;

import com.cangshuo.toolbox.common.exception.*;
import java.net.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanzouBoundaryTest {
    private static void rejects(ApiError expected, Runnable action) {
        assertEquals(expected, assertThrows(ApiException.class, action::run).error());
    }
    @Test void extractsOneOfficialLinkPasswordAndRemovesPasswordFromRefererQuery() {
        var probe = URI.create("https://www.lanzouf.com/iABC123?pwd=8xYz&source=share");
        assertTrue(LanzouHosts.isOwned(probe), probe.toString());
        assertEquals(probe, LanzouHosts.page(probe));
        var input = LanzouInput.parse("复制这段内容 https://www.lanzouf.com/iABC123?pwd=8xYz&source=share 提取码：8xYz ，打开蓝奏云", "");
        assertEquals("https://www.lanzouf.com/iABC123?source=share", input.uri().toString());
        assertEquals("8xYz", input.password());
        assertEquals("explicit", LanzouInput.parse("https://lanzoux.com/i1 密码：abcd", "explicit").password());
        assertEquals("https://lanzoux.com/i1", LanzouInput.parse("http://lanzoux.com/i1", null).uri().toString());
    }
    @Test void rejectsAmbiguousLinksLookalikesCredentialsPortsAndControlText() {
        for (String text : new String[]{"", "x".repeat(4097), "https://lanzoux.com/a\u0000", "javascript:alert(1)"})
            rejects(ApiError.INVALID_ARGUMENT, () -> LanzouInput.parse(text, null));
        for (String text : new String[]{"https://lanzoux.com/a https://lanzouw.com/b", "https://u:p@lanzoux.com/a",
                "https://lanzoux.com:8443/a", "https://lanzoux.com./a", "https://lanzoux.com.evil.example/a",
                "https://a.b.lanzou.com/a", "https://127.0.0.1/a", "https://not-lanzou.com/a",
                "https://example.com/?url=https%3A%2F%2Flanzoux.com"})
            rejects(ApiError.LANZOU_LINK_INVALID, () -> LanzouInput.parse(text, null));
        rejects(ApiError.INVALID_ARGUMENT, () -> LanzouInput.parse("https://lanzoux.com/a", "bad\r\nHost: evil"));
    }
    @Test void acceptsCurrentHostFamiliesButNotMultiLabelOrSuffixSpoofs() {
        for (String host : new String[]{"lanzou.com", "www.lanzouf.com", "ww1.lanzoux.com",
                "api.lanzoui.com", "apifile.lanzouw.com", "developer.lanzoug.com"})
            assertTrue(LanzouHosts.isOwned(URI.create("https://" + host + "/x")), host);
        for (String host : new String[]{"evil-lanzou.com", "lanzou.com.evil.example", "a.b.lanzou.com",
                "lanzouuu.com", "www.lanzou.com.evil"})
            assertFalse(LanzouHosts.isOwned(URI.create("https://" + host + "/x")), host);
    }
    @Test void directUrlsAreHttpOnlyPublicNamedHostsAndLoseOnlyPid() {
        URI direct = LanzouHosts.direct("http://cdn.example.com/file/a.zip?pid=1.2.3.4&fn=a.zip&signature=A%2FB");
        assertEquals("http://cdn.example.com/file/a.zip?fn=a.zip&signature=A%2FB", direct.toString());
        for (String value : new String[]{"javascript:alert(1)", "file:///etc/passwd", "https://u:p@x.com/a",
                "https://127.0.0.1/a", "https://[::1]/a", "https://localhost/a", "https://intranet/a",
                "https://x.com:8080/a", "https://x.com/a#fragment"})
            rejects(ApiError.LANZOU_PROVIDER_ERROR, () -> LanzouHosts.direct(value));
    }
    @Test void currentDownloadResponseFamiliesAreAcceptedButLookalikesAreNot() {
        for (String host : new String[]{"slsstm2.dmpdmp.com", "developer4.lanrar.com",
                "developer.lanzoug.com", "cdn.woozooo.com"})
            assertDoesNotThrow(() -> LanzouHosts.downloadPage(URI.create("https://" + host + "/file/a")));
        for (String host : new String[]{"dmpdmp.com.evil.example", "evil-dmpdmp.com", "localhost", "127.0.0.1"})
            rejects(ApiError.LANZOU_PROVIDER_ERROR, () -> LanzouHosts.downloadPage(URI.create("https://" + host + "/file/a")));
    }
    @Test void dnsResolverRefusesPrivateAndTransitionAddresses() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "169.254.169.254", "192.168.1.1",
                "100.64.0.1", "198.18.0.1", "::1", "fc00::1", "2001:db8::1", "::ffff:127.0.0.1"})
            assertFalse(LanzouHttp.LanzouDns.publicAddress(InetAddress.getByName(ip)), ip);
        for (String ip : new String[]{"1.1.1.1", "8.8.8.8", "2606:4700:4700::1111"})
            assertTrue(LanzouHttp.LanzouDns.publicAddress(InetAddress.getByName(ip)), ip);
        assertThrows(UnknownHostException.class, () -> new LanzouHttp.LanzouDns().resolve("127.0.0.1"));
    }
    @Test void deterministicDownloadHandshakeMatchesReviewedAlgorithm() {
        String arg1 = "0123456789abcdef0123456789abcdef01234567";
        assertEquals("d2c7186598ab1a508a4f6064e4fa746323ab17c6", LanzouChallenge.solve(arg1));
        assertEquals("sid=abc; acw_sc__v2=" + LanzouChallenge.solve(arg1),
                LanzouChallenge.addCookie("sid=abc", LanzouChallenge.solve(arg1)));
        assertEquals("sid=abc; acw_sc__v2=" + LanzouChallenge.solve(arg1),
                LanzouChallenge.addCookie("sid=abc; acw_sc__v2=" + "f".repeat(40), LanzouChallenge.solve(arg1)));
        assertFalse(LanzouChallenge.isChallenge("ordinary page"));
        assertEquals("", LanzouChallenge.solve("short"));
    }
}
