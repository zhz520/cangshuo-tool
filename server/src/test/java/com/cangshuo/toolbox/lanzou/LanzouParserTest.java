package com.cangshuo.toolbox.lanzou;

import com.cangshuo.toolbox.common.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LanzouParserTest {
    private final LanzouHttp http = mock(LanzouHttp.class);
    private final LanzouParser parser = new LanzouParser(http, new ObjectMapper());
    private final URI share = URI.create("https://www.lanzouf.com/iAbC123");
    private static void rejects(ApiError expected, Runnable action) {
        assertEquals(expected, assertThrows(ApiException.class, action::run).error());
    }
    private String page(String body) {
        return "<html><head><title>示例文件.txt - 蓝奏云</title></head><body><div class=\"n_box_3fn\">示例文件.txt</div>"
                + "<div class=\"n_filesize\">大小：12.5 KB</div>" + body + "</body></html>";
    }
    @Test void newPageUsesIframeVariableSignAbsoluteAjaxAndDirectRedirect() {
        URI frame = URI.create("https://www.lanzouf.com/fn?abc");
        String detail = page("<script>var wp_sign='inline';var ajaxdata='web';var domain1='https://apifile.woozooo.com/ajaxfile.php?file=88';"
                + "var isngis='variable-sign';$.ajax({url:domain1,data:{'sign':isngis,'kd':1}});</script>");
        when(http.get(eq(share), anyString(), eq(share.toString()), anyLong()))
                .thenReturn(new LanzouHttp.Document(share, page("<iframe src='/fn?abc'></iframe>"), "sid=1"));
        when(http.get(eq(frame), anyString(), eq(share.toString()), anyLong()))
                .thenReturn(new LanzouHttp.Document(frame, detail, "sid=1"));
        when(http.post(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new LanzouHttp.Document(frame, "{\"zt\":1,\"inf\":\"示例文件.txt\",\"dom\":\"https://download.lanzouf.com\",\"url\":\"abc\"}", "sid=1"));
        when(http.probe(any(), anyString(), anyString(), anyLong()))
                .thenReturn(new LanzouHttp.Probe(302, URI.create("https://cdn.example.com/a.txt?pid=1&fn=a.txt"), "", "sid=1"));
        var result = parser.parse(new LanzouInput(share, ""), System.nanoTime() + 1_000_000_000L);
        assertEquals(LanzouParser.State.DIRECT, result.state());
        assertEquals("示例文件.txt", result.fileName()); assertEquals("12.5 KB", result.fileSize());
        assertEquals("https://cdn.example.com/a.txt?fn=a.txt", result.directUri().toString());
        verify(http).post(eq(URI.create("https://apifile.woozooo.com/ajaxfile.php?file=88")),
                argThat(form -> form.contains("sign=variable-sign") && form.contains("websignkey=web")), anyString(), anyString(), anyLong());
    }
    @Test void passwordPageRequiresAndClassifiesPasswordWithoutPersistingIt() {
        String html = page("<script>function down_p(){} 'sign':'abc';</script><div class='/ajaxm.php?file=7'></div>");
        when(http.get(any(), anyString(), anyString(), anyLong())).thenReturn(new LanzouHttp.Document(share, html, ""));
        rejects(ApiError.LANZOU_PASSWORD_REQUIRED, () -> parser.parse(new LanzouInput(share, ""), System.nanoTime() + 1_000_000_000L));
        when(http.post(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new LanzouHttp.Document(share, "{\"zt\":0,\"inf\":\"密码错误\"}", ""));
        rejects(ApiError.LANZOU_PASSWORD_INVALID,
                () -> parser.parse(new LanzouInput(share, "1234"), System.nanoTime() + 1_000_000_000L));
        verify(http).post(any(), argThat(form -> form.contains("p=1234")), anyString(), anyString(), anyLong());
    }
    @Test void verificationAndRestrictionsFallBackToOfficialPageWithoutInventingDirectLink() {
        String html = page("<script>var filename = 'a.zip';'sign':'s';/ajaxfile.php?file=1</script>");
        when(http.get(any(), anyString(), anyString(), anyLong())).thenReturn(new LanzouHttp.Document(share, html, ""));
        when(http.post(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new LanzouHttp.Document(share, "{\"zt\":1,\"dom\":\"https://download.lanzouf.com\",\"url\":\"x\"}", ""));
        when(http.probe(any(), anyString(), anyString(), anyLong()))
                .thenReturn(new LanzouHttp.Probe(200, null, "<script>var arg1='abc'; acw_sc__v2</script>", "sid=1"));
        var result = parser.parse(new LanzouInput(share, ""), System.nanoTime() + 1_000_000_000L);
        assertEquals(LanzouParser.State.OFFICIAL_PAGE, result.state()); assertTrue(result.verificationRequired());
        assertNull(result.directUri());
    }
    @Test void removedFolderAndChallengePagesAreDistinct() {
        when(http.get(any(), anyString(), anyString(), anyLong()))
                .thenReturn(new LanzouHttp.Document(share, page("文件取消分享了"), ""));
        rejects(ApiError.LANZOU_FILE_UNAVAILABLE, () -> parser.parse(new LanzouInput(share, ""), System.nanoTime() + 1_000_000_000L));
        when(http.get(any(), anyString(), anyString(), anyLong()))
                .thenReturn(new LanzouHttp.Document(share, page("<div class='file-list'>文件夹 文件数目</div>"), ""));
        rejects(ApiError.LANZOU_FOLDER_UNSUPPORTED, () -> parser.parse(new LanzouInput(share, ""), System.nanoTime() + 1_000_000_000L));
        when(http.get(any(), anyString(), anyString(), anyLong()))
                .thenReturn(new LanzouHttp.Document(share, page("<script>var arg1='x';acw_sc__v2</script>"), ""));
        rejects(ApiError.LANZOU_VERIFICATION_REQUIRED, () -> parser.parse(new LanzouInput(share, ""), System.nanoTime() + 1_000_000_000L));
    }
    @Test void malformedAjaxAndUntrustedEndpointNeverBecomeSuccess() {
        String html = page("<script>'sign':'s';/ajaxfile.php?file=1</script>");
        when(http.get(any(), anyString(), anyString(), anyLong())).thenReturn(new LanzouHttp.Document(share, html, ""));
        when(http.post(any(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new LanzouHttp.Document(share, "not-json", ""));
        rejects(ApiError.LANZOU_PROVIDER_ERROR, () -> parser.parse(new LanzouInput(share, ""), System.nanoTime() + 1_000_000_000L));
        when(http.get(any(), anyString(), anyString(), anyLong()))
                .thenReturn(new LanzouHttp.Document(share, page("<script>'sign':'s';https://evil.example/ajaxfile.php?file=1</script>"), ""));
        rejects(ApiError.LANZOU_PROVIDER_ERROR, () -> parser.parse(new LanzouInput(share, ""), System.nanoTime() + 1_000_000_000L));
    }
}
