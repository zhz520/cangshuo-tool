package com.cangshuo.toolbox.lanzou;

import com.cangshuo.toolbox.common.exception.*;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.*;
import org.springframework.stereotype.Component;

/** Fixed-identity, bounded HTTP access. Deliberately sends no forwarding or client-IP headers. */
@Component
public class LanzouHttp implements AutoCloseable {
    private static final int MAX_DOCUMENT = 2 * 1024 * 1024;
    private static final int MAX_PROBE = 128 * 1024;
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36";
    private final CloseableHttpClient client;

    public LanzouHttp() {
        this(HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new LanzouDns()).setMaxConnTotal(8).setMaxConnPerRoute(4)
                        .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(4))
                                .setSocketTimeout(Timeout.ofSeconds(7)).setTimeToLive(TimeValue.ofSeconds(45)).build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofSeconds(2))
                        .setResponseTimeout(Timeout.ofSeconds(8)).build())
                .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build());
    }

    LanzouHttp(CloseableHttpClient client) { this.client = client; }

    record Document(URI uri, String body, String cookies) {}
    record Probe(int status, URI location, String body, String cookies) {}

    public Document get(URI uri, String cookies, String referer, long deadline) {
        return fetch(uri, false, null, cookies, referer, deadline, false);
    }

    public Document post(URI uri, String form, String cookies, String referer, long deadline) {
        return fetch(uri, true, form, cookies, referer, deadline, true);
    }

    public Probe probe(URI uri, String cookies, String referer, long deadline) {
        LanzouHosts.downloadPage(uri);
        String jar = cookies == null ? "" : cookies;
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                checkDeadline(deadline);
                var request = request(uri, false, null, jar, referer);
                try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
                    int status = response.getCode();
                    URI location = location(uri, response);
                    jar = mergeCookies(uri.getHost(), jar, response);
                    String body = "";
                    if (status == 200 && response.getEntity() != null && isText(response)) {
                        try (InputStream input = response.getEntity().getContent()) {
                            body = new String(readLimited(input, MAX_PROBE, deadline), charset(response));
                        }
                    }
                    if (attempt == 0 && status == 200 && location == null && LanzouChallenge.isChallenge(body)) {
                        String verified = LanzouChallenge.addCookie(jar, LanzouChallenge.cookie(body));
                        if (!verified.equals(jar)) { jar = verified; continue; }
                    }
                    return new Probe(status, location, body, jar);
                }
            }
            throw provider();
        } catch (ApiException error) {
            throw error;
        } catch (SocketTimeoutException error) {
            throw new ApiException(ApiError.LANZOU_TIMEOUT);
        } catch (Exception error) {
            throw new ApiException(ApiError.LANZOU_PROVIDER_ERROR);
        }
    }

    private Document fetch(URI initial, boolean post, String form, String cookies, String referer, long deadline,
                           boolean allowDownloadHost) {
        URI current = initial;
        String jar = cookies == null ? "" : cookies;
        boolean challengeRetried = false;
        for (int attempt = 0; attempt <= 4; attempt++) {
            if (allowDownloadHost) LanzouHosts.downloadPage(current);
            else LanzouHosts.ownedPage(current);
            checkDeadline(deadline);
            var request = request(current, post, form, jar, referer);
            try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
                int status = response.getCode();
                String nextCookies = mergeCookies(current.getHost(), jar, response);
                if (redirect(status)) {
                    URI next = location(current, response);
                    if (next == null || attempt == 4) throw provider();
                    if (allowDownloadHost) LanzouHosts.downloadPage(next);
                    else LanzouHosts.ownedPage(next);
                    jar = current.getHost().equalsIgnoreCase(next.getHost()) ? nextCookies : "";
                    current = next;
                    if (status == 301 || status == 302 || status == 303) post = false;
                    form = null;
                    continue;
                }
                if (status == 401 || status == 403 || status == 429) throw restricted();
                if (status == 404 || status == 410) throw new ApiException(ApiError.LANZOU_FILE_UNAVAILABLE);
                if (status != 200 || response.getEntity() == null) throw provider();
                try (InputStream input = response.getEntity().getContent()) {
                    String body = new String(readLimited(input, MAX_DOCUMENT, deadline), charset(response));
                    if (!challengeRetried && LanzouChallenge.isChallenge(body)) {
                        String verified = LanzouChallenge.addCookie(nextCookies, LanzouChallenge.cookie(body));
                        if (!verified.equals(nextCookies)) {
                            jar = verified; challengeRetried = true;
                            continue;
                        }
                    }
                    return new Document(current, body, nextCookies);
                }
            } catch (ApiException error) {
                throw error;
            } catch (SocketTimeoutException error) {
                throw new ApiException(ApiError.LANZOU_TIMEOUT);
            } catch (Exception error) {
                throw provider();
            }
        }
        throw provider();
    }

    private static HttpUriRequestBase request(URI uri, boolean post, String form, String cookies, String referer) {
        HttpUriRequestBase value = post ? new HttpPost(uri) : new HttpGet(uri);
        if (value instanceof HttpPost postRequest && form != null)
            postRequest.setEntity(new StringEntity(form, ContentType.APPLICATION_FORM_URLENCODED));
        value.setHeader("User-Agent", UA);
        value.setHeader("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
        value.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6");
        value.setHeader("Accept-Encoding", "identity");
        value.setHeader("Cache-Control", "no-cache");
        if (!cookies.isBlank()) value.setHeader("Cookie", cookies);
        if (referer != null && !referer.isBlank()) value.setHeader("Referer", referer);
        return value;
    }

    private static URI location(URI base, ClassicHttpResponse response) {
        Header header = response.getFirstHeader("Location");
        if (header == null || header.getValue().isBlank()) return null;
        try { return base.resolve(header.getValue().strip()); }
        catch (IllegalArgumentException error) { return null; }
    }

    private static String mergeCookies(String host, String current, ClassicHttpResponse response) {
        if (response.getHeaders("Set-Cookie") == null) return current;
        Map<String, String> cookie = new LinkedHashMap<>();
        for (String pair : current.split(";")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && equals < 65) cookie.put(pair.substring(0, equals).strip(), pair.substring(equals + 1).strip());
        }
        for (Header header : response.getHeaders("Set-Cookie")) {
            String first = header.getValue().split(";", 2)[0].strip();
            int equals = first.indexOf('=');
            if (equals <= 0 || equals >= 65 || first.length() > 1200
                    || !first.substring(0, equals).matches("[A-Za-z0-9_-]+")
                    || first.codePoints().anyMatch(Character::isISOControl)) continue;
            cookie.put(first.substring(0, equals), first.substring(equals + 1));
        }
        String value = String.join("; ", cookie.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
        return value.length() <= 4096 ? value : value.substring(0, 4096);
    }

    private static Charset charset(ClassicHttpResponse response) {
        String type = Optional.ofNullable(response.getFirstHeader("Content-Type")).map(Header::getValue).orElse("");
        for (String part : type.split(";")) {
            String value = part.strip();
            if (value.startsWith("charset=")) {
                try { return Charset.forName(value.substring(8).replace("\"", "").strip()); }
                catch (Exception ignored) { return StandardCharsets.UTF_8; }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static boolean isText(ClassicHttpResponse response) {
        String type = Optional.ofNullable(response.getFirstHeader("Content-Type")).map(Header::getValue).orElse("")
                .toLowerCase(Locale.ROOT);
        return type.isBlank() || type.startsWith("text/") || type.contains("json")
                || type.contains("javascript") || type.contains("xhtml");
    }

    private static byte[] readLimited(InputStream input, int max, long deadline) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, 16384));
        byte[] buffer = new byte[16384];
        for (int count; (count = input.read(buffer)) != -1;) {
            checkDeadline(deadline);
            if ((long) out.size() + count > max) throw provider();
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private static void checkDeadline(long deadline) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline)
            throw new ApiException(ApiError.LANZOU_TIMEOUT);
    }

    private static boolean redirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }
    private static ApiException restricted() { return new ApiException(ApiError.LANZOU_RESTRICTED); }
    private static ApiException provider() { return new ApiException(ApiError.LANZOU_PROVIDER_ERROR); }
    private static boolean hasControl(String value) { return LanzouHosts.hasControl(value); }

    @PreDestroy @Override public void close() throws IOException { client.close(); }

    /** DNS results used by the socket connection must all be public. */
    static final class LanzouDns implements DnsResolver {
        @Override public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) throw new UnknownHostException("No address");
            for (InetAddress address : addresses) if (!publicAddress(address)) throw new UnknownHostException("Private address");
            return addresses;
        }
        @Override public String resolveCanonicalHostname(String host) throws UnknownHostException { resolve(host); return host; }
        static boolean publicAddress(InetAddress ip) {
            if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress()
                    || ip.isSiteLocalAddress() || ip.isMulticastAddress()) return false;
            byte[] b = ip.getAddress();
            if (b.length == 4) {
                int a = b[0] & 255, c = b[1] & 255, d = b[2] & 255;
                return !(a == 0 || a == 10 || a == 127 || a >= 224 || a == 169 && c == 254
                        || a == 100 && c >= 64 && c <= 127 || a == 172 && c >= 16 && c <= 31
                        || a == 192 && (c == 168 || c == 0 || c == 88 && d == 99)
                        || a == 198 && (c == 18 || c == 19 || c == 51 && d == 100)
                        || a == 203 && c == 0 && d == 113);
            }
            return b.length == 16 && (b[0] & 0xe0) == 0x20
                    && !(b[0] == 0x20 && b[1] == 1 && ((b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8
                    || b[2] == 0 || b[2] == 0x10)) && !(b[0] == 0x20 && b[1] == 2);
        }
    }
}
