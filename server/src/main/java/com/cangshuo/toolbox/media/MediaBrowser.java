package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Internal typed adapter: no public arbitrary URL, browser code or CDP endpoint is exposed. */
@Component
public class MediaBrowser {
    private final String base;
    private final MediaJson json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public MediaBrowser(@Value("${toolbox.media.browser-url:}") String base, MediaJson json) {
        this.base = base.strip().replaceAll("/+$", ""); this.json = json;
        if (!this.base.isEmpty()) {
            URI uri = URI.create(this.base);
            if (!java.util.List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
                throw new IllegalArgumentException("Invalid internal media browser endpoint");
        }
    }
    public boolean enabled() { return !base.isEmpty(); }
    public JsonNode douyin(String id, long deadline) {
        if (!enabled() || !id.matches("[0-9]{5,25}")) return null;
        MediaHttp.checkDeadline(deadline);
        long millis = Math.min(24_000, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
        if (millis < 1000) throw new ApiException(ApiError.MEDIA_TIMEOUT);
        var request = HttpRequest.newBuilder(URI.create(base + "/douyin/" + id)).timeout(Duration.ofMillis(millis)).GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                JsonNode root = json.json(new String(MediaHttp.readLimited(stream, 512_000, deadline), java.nio.charset.StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    if (root.path("code").asInt(0) == 10008) throw new ApiException(ApiError.SERVICE_UNAVAILABLE);
                    if (root.path("code").asInt(0) == 61005) throw new ApiException(ApiError.MEDIA_TOO_LARGE);
                    if (root.path("code").asInt(0) == 61002) throw new ApiException(ApiError.MEDIA_UNAVAILABLE);
                    throw new ApiException(ApiError.MEDIA_PLATFORM_RESTRICTED);
                }
                JsonNode data = root.path("aweme");
                if (!id.equals(data.path("aweme_id").asText("")) || !MediaParser.byteDanceImages(data).isArray()
                        || MediaParser.byteDanceImages(data).isEmpty()) throw new ApiException(ApiError.MEDIA_PLATFORM_RESTRICTED);
                return data;
            }
        } catch (ApiException error) { throw error; }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new ApiException(ApiError.MEDIA_TIMEOUT); }
        catch (java.net.http.HttpTimeoutException error) { throw new ApiException(ApiError.MEDIA_TIMEOUT); }
        catch (Exception error) { throw new ApiException(ApiError.MEDIA_PROVIDER_ERROR); }
    }
}
