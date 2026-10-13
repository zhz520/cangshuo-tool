package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MediaBrowserTest {
    private HttpServer server;
    private MediaBrowser browser;
    private final AtomicInteger requests = new AtomicInteger();
    private int status = 200;
    private String body = "{\"aweme\":{\"aweme_id\":\"7589870649747125477\",\"images\":[{\"url_list\":[\"https://p.douyinpic.com/a.jpg\"]}]}}";
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            assertEquals("/douyin/7589870649747125477", exchange.getRequestURI().toString());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        browser = new MediaBrowser("http://127.0.0.1:" + server.getAddress().getPort(), new MediaJson());
    }
    @AfterEach void stop() { server.stop(0); }
    private void rejects(ApiError expected) {
        assertEquals(expected, assertThrows(ApiException.class,
                () -> browser.douyin("7589870649747125477", MediaHttp.deadline(3))).error());
    }
    @Test void typedEndpointAcceptsOnlyMatchingCompleteAlbum() {
        assertEquals(1, browser.douyin("7589870649747125477", MediaHttp.deadline(3)).path("images").size());
        body = body.replace("7589870649747125477", "11111");
        rejects(ApiError.MEDIA_PLATFORM_RESTRICTED);
        body = "{\"aweme\":{\"aweme_id\":\"7589870649747125477\",\"images\":[]}}";
        rejects(ApiError.MEDIA_PLATFORM_RESTRICTED);
    }
    @Test void disabledOrInvalidIdentifiersNeverCallInternalService() {
        assertFalse(new MediaBrowser("", new MediaJson()).enabled());
        assertNull(browser.douyin("../../health", MediaHttp.deadline(3)));
        assertNull(browser.douyin("12345?url=http://localhost", MediaHttp.deadline(3)));
        assertEquals(0, requests.get());
        assertThrows(IllegalArgumentException.class, () -> new MediaBrowser("http://u:p@localhost", new MediaJson()));
        assertThrows(IllegalArgumentException.class, () -> new MediaBrowser("http://localhost/path", new MediaJson()));
    }
    @Test void upstreamRestrictionSizeLimitAndDeadlineStayDistinct() {
        status = 503; body = "{\"code\":10008}"; rejects(ApiError.SERVICE_UNAVAILABLE);
        status = 503; body = "{\"code\":61009}"; rejects(ApiError.MEDIA_PLATFORM_RESTRICTED);
        status = 413; body = "{\"code\":61005}"; rejects(ApiError.MEDIA_TOO_LARGE);
        status = 200; body = " ".repeat(512_001); rejects(ApiError.MEDIA_TOO_LARGE);
        assertEquals(ApiError.MEDIA_TIMEOUT, assertThrows(ApiException.class,
                () -> browser.douyin("7589870649747125477", System.nanoTime() - 1)).error());
    }
}
