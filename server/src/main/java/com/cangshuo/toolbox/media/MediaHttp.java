package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.*;
import org.springframework.stereotype.Component;

@Component
public class MediaHttp {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MediaHttp.class);
    private static final java.util.concurrent.atomic.AtomicLong LAST_FAILURE_LOG = new java.util.concurrent.atomic.AtomicLong();
    /** Optional operator cookie for Xiaohongshu only; that platform risk-controls shared hosting addresses. */
    @org.springframework.beans.factory.annotation.Value("${toolbox.media.xhs-cookie:}")
    private String xhsCookie = "";
    static final int MAX_DOCUMENT_BYTES = 6 * 1024 * 1024;
    static final long MAX_FILE_BYTES = 200L * 1024 * 1024;
    static final String MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1";
    static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36";
    /** Douyin mobile feed client; that endpoint rejects browser user agents. */
    static final String APP_UA = "com.ss.android.ugc.aweme/300401 (Linux; U; Android 13; zh_CN; Pixel 6; Build/TQ3A.230805.001; Cronet/58.0.2991.0)";
    private final CloseableHttpClient client = HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(new PublicMediaDns())
                    .setMaxConnTotal(16).setMaxConnPerRoute(8)
                    .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(4))
                            .setSocketTimeout(Timeout.ofSeconds(6)).setTimeToLive(TimeValue.ofSeconds(60)).build()).build())
            .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofSeconds(2))
                    .setResponseTimeout(Timeout.ofSeconds(6)).build())
            .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement().build();

    @PreDestroy public void close() throws IOException { client.close(); }
    public record Document(URI uri, String text, List<String> cookies) {}
    public Document document(MediaPlatform platform, URI uri, String form, boolean desktop, long deadline) {
        return document(platform, uri, form, desktop ? DESKTOP_UA : MOBILE_UA, deadline);
    }
    public Document document(MediaPlatform platform, URI uri, String form, String userAgent, long deadline) {
        URI current = uri;
        String cookie = "";
        for (int redirect = 0; redirect <= 5; redirect++) {
            platform.validatePage(current);
            checkDeadline(deadline);
            var request = request(current, platform, userAgent, form);
            String operatorCookie = platformCookie(platform, xhsCookie);
            if (!operatorCookie.isEmpty()) request.setHeader("Cookie", operatorCookie);
            else if (!cookie.isEmpty()) request.setHeader("Cookie", cookie);
            try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
                int status = response.getCode();
                if (isRedirect(status)) {
                    Header location = response.getFirstHeader("Location");
                    if (location == null || redirect == 5) throw provider();
                    URI next = current.resolve(location.getValue());
                    platform.validatePage(next);
                    // Session cookies only stay on the identical host; never forward response headers as request headers.
                    cookie = current.getHost().equals(next.getHost()) ? cookieHeader(response) : "";
                    current = next; form = null;
                    continue;
                }
                if (status == 401 || status == 403 || status == 412 || status == 429)
                    throw new ApiException(ApiError.MEDIA_PLATFORM_RESTRICTED);
                if (status == 404 || status == 410) throw new ApiException(ApiError.MEDIA_LINK_EXPIRED);
                if (status != 200 || response.getEntity() == null) throw provider();
                if (response.getEntity().getContentLength() > MAX_DOCUMENT_BYTES) throw tooLarge();
                try (InputStream stream = response.getEntity().getContent()) {
                    return new Document(current, new String(readLimited(stream, MAX_DOCUMENT_BYTES, deadline), StandardCharsets.UTF_8), List.of());
                }
            } catch (ApiException error) { throw error; }
            catch (java.net.SocketTimeoutException error) { throw new ApiException(ApiError.MEDIA_TIMEOUT); }
            catch (Exception error) { logFailure(current, error); throw provider(); }
        }
        throw provider();
    }

    /** Opaque asset handles are resolved by the service before this method; there is no arbitrary URL endpoint. */
    public Download open(MediaPlatform platform, URI uri, String range, long deadline) {
        URI current = uri;
        for (int redirect = 0; redirect <= 5; redirect++) {
            platform.validateMedia(current);
            checkDeadline(deadline);
            // Media downloads use the desktop browser profile so CDNs see a normal player request.
            var request = request(current, platform, DESKTOP_UA, null);
            request.setHeader("Accept-Encoding", "identity");
            if (range != null) request.setHeader("Range", range);
            ClassicHttpResponse response = null;
            try {
                response = client.executeOpen(null, request, null);
                int status = response.getCode();
                if (isRedirect(status)) {
                    Header location = response.getFirstHeader("Location");
                    if (location == null || redirect == 5) throw provider();
                    current = current.resolve(location.getValue());
                    response.close();
                    continue;
                }
                if (status == 403 || status == 412 || status == 429) throw new ApiException(ApiError.MEDIA_PLATFORM_RESTRICTED);
                if (status == 404 || status == 410) throw new ApiException(ApiError.MEDIA_UNAVAILABLE);
                if (status == 416) throw new ApiException(ApiError.INVALID_ARGUMENT);
                if (status != 200 && status != 206 || response.getEntity() == null) throw provider();
                long length = response.getEntity().getContentLength();
                if (length > MAX_FILE_BYTES) throw tooLarge();
                String type = Optional.ofNullable(response.getFirstHeader("Content-Type")).map(Header::getValue)
                        .orElse("").split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
                String contentRange = Optional.ofNullable(response.getFirstHeader("Content-Range")).map(Header::getValue).orElse(null);
                validateContentRange(status, range, contentRange, length);
                var input = new PushbackInputStream(response.getEntity().getContent(), 24);
                if (type.isEmpty() || type.equals("application/octet-stream") || type.equals("binary/octet-stream")) {
                    if (status == 206 && contentRange != null && !contentRange.startsWith("bytes 0-")) {
                        // A nonzero range cannot be sniffed; permit only explicit MP4/MOV path extensions.
                        String path = current.getPath().toLowerCase(Locale.ROOT);
                        type = path.endsWith(".mp4") ? "video/mp4" : path.endsWith(".mov") ? "video/quicktime" : "";
                    } else {
                        byte[] prefix = input.readNBytes(24); input.unread(prefix); type = sniffType(prefix);
                    }
                }
                if (!List.of("image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/quicktime").contains(type))
                    throw new ApiException(ApiError.MEDIA_FORMAT_UNSUPPORTED);
                return new Download(response, input, type, length, status, contentRange, deadline);
            } catch (ApiException error) { closeQuietly(response); throw error; }
            catch (java.net.SocketTimeoutException error) { closeQuietly(response); throw new ApiException(ApiError.MEDIA_TIMEOUT); }
            catch (Exception error) { closeQuietly(response); logFailure(current, error); throw provider(); }
        }
        throw provider();
    }

    private static HttpUriRequestBase request(URI uri, MediaPlatform platform, String userAgent, String form) {
        HttpUriRequestBase request = form == null ? new HttpGet(uri) : new HttpPost(uri);
        if (form != null) ((HttpPost) request).setEntity(new StringEntity(form, ContentType.APPLICATION_FORM_URLENCODED));
        request.setHeader("User-Agent", userAgent);
        request.setHeader("Referer", platform.referer());
        request.setHeader("Accept", "*/*");
        return request;
    }
    /** Only a well-formed cookie is ever attached, and only to the Xiaohongshu hosts. */
    static String platformCookie(MediaPlatform platform, String cookie) {
        if (platform != MediaPlatform.XIAOHONGSHU || cookie == null) return "";
        String value = cookie.strip();
        return value.matches("[A-Za-z0-9_\\-=.%; ]{8,1024}") ? value : "";
    }
    private static String cookieHeader(ClassicHttpResponse response) {
        var values = new ArrayList<String>();
        for (Header header : response.getHeaders("Set-Cookie")) {
            String value = header.getValue().split(";", 2)[0];
            if (value.length() <= 1024 && value.matches("[A-Za-z0-9_-]+=[\\x21-\\x7E]*")) values.add(value);
        }
        return String.join("; ", values).substring(0, Math.min(4096, String.join("; ", values).length()));
    }
    static String sniffType(byte[] p) {
        if (p.length >= 3 && (p[0] & 255) == 255 && (p[1] & 255) == 216 && (p[2] & 255) == 255) return "image/jpeg";
        if (p.length >= 8 && Arrays.equals(Arrays.copyOf(p, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if (p.length >= 6 && (new String(p,0,6,StandardCharsets.US_ASCII).equals("GIF87a") || new String(p,0,6,StandardCharsets.US_ASCII).equals("GIF89a"))) return "image/gif";
        if (p.length >= 12 && new String(p,0,4,StandardCharsets.US_ASCII).equals("RIFF") && new String(p,8,4,StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        if (p.length >= 12 && new String(p,4,4,StandardCharsets.US_ASCII).equals("ftyp")) return new String(p,8,4,StandardCharsets.US_ASCII).equals("qt  ") ? "video/quicktime" : "video/mp4";
        return "";
    }
    static void validateContentRange(int status, String requested, String actual, long length) {
        if (status == 200) { if (actual != null) throw provider(); return; }
        if (requested == null || actual == null) throw provider();
        var matcher = java.util.regex.Pattern.compile("bytes ([0-9]{1,12})-([0-9]{1,12})/([0-9]{1,12})").matcher(actual);
        if (!matcher.matches()) throw provider();
        long start = Long.parseLong(matcher.group(1)), end = Long.parseLong(matcher.group(2)), total = Long.parseLong(matcher.group(3));
        if (total > MAX_FILE_BYTES) throw tooLarge();
        String[] expected = requested.substring(6).split("-", -1);
        if (end < start || end >= total || start != Long.parseLong(expected[0])
                || !expected[1].isEmpty() && end > Long.parseLong(expected[1])
                || length >= 0 && length != end - start + 1) throw provider();
    }
    public static long deadline(int seconds) { return System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds); }
    static void checkDeadline(long deadline) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) throw new ApiException(ApiError.MEDIA_TIMEOUT);
    }
    static byte[] readLimited(InputStream input, int max, long deadline) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, 16384));
        byte[] buffer = new byte[16384];
        for (int count; (count = input.read(buffer)) != -1;) {
            checkDeadline(deadline);
            if ((long) out.size() + count > max) throw tooLarge();
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
    public record Download(ClassicHttpResponse response, InputStream input, String contentType, long length,
                           int status, String contentRange, long deadline) implements AutoCloseable {
        public String extension() { return switch (contentType) {
            case "image/png" -> "png"; case "image/webp" -> "webp"; case "image/gif" -> "gif";
            case "video/mp4", "audio/mp4" -> "mp4"; case "video/quicktime" -> "mov"; case "audio/mpeg" -> "mp3";
            default -> "jpg";
        }; }
        public void writeTo(OutputStream output, long budget) throws IOException {
            byte[] buffer = new byte[32768]; long total = 0;
            for (int n; (n = input.read(buffer)) != -1;) {
                checkDeadline(deadline); total += n;
                if (total > budget) throw tooLarge();
                output.write(buffer, 0, n);
            }
            if (length >= 0 && total != length) throw new IOException("Incomplete media transfer");
        }
        @Override public void close() throws IOException { response.close(); }
    }
    private static boolean isRedirect(int code) { return code == 301 || code == 302 || code == 303 || code == 307 || code == 308; }
    /** Bounded diagnostics for provider failures: host and exception type only, never URLs, queries or bodies. */
    static void logFailure(URI uri, Exception error) {
        long now = System.currentTimeMillis();
        long previous = LAST_FAILURE_LOG.get();
        if (now - previous < 60_000L || !LAST_FAILURE_LOG.compareAndSet(previous, now)) return;
        LOG.warn("Media provider request failed; host={} type={}", uri == null ? "unknown" : uri.getHost(),
                error.getClass().getSimpleName());
    }
    private static void closeQuietly(ClassicHttpResponse response) { if (response != null) try { response.close(); } catch (IOException ignored) {} }
    private static ApiException provider() { return new ApiException(ApiError.MEDIA_PROVIDER_ERROR); }
    private static ApiException tooLarge() { return new ApiException(ApiError.MEDIA_TOO_LARGE); }
}
