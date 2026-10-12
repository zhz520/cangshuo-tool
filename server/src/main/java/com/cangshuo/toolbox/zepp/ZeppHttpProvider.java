package com.cangshuo.toolbox.zepp;

import com.cangshuo.toolbox.common.exception.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.stereotype.Component;

/** Fixed Zepp/Huami origins, no redirects/cookies/retries, bounded responses and one total deadline. */
@Component
public class ZeppHttpProvider implements ZeppProvider {
    static final URI REGISTRATION = URI.create("https://api-user.zepp.com/v2/registrations/tokens");
    static final URI CLIENT_LOGIN = URI.create("https://account.huami.com/v2/client/login");
    static final String DATA_ORIGIN = "https://api-mifit-cn.huami.com";
    static final String APP = "com.xiaomi.hm.health";
    static final int MAX_RESPONSE_BYTES = 65536;
    private final ZeppProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Clock clock;
    private final Duration budget;

    @org.springframework.beans.factory.annotation.Autowired
    public ZeppHttpProvider(ZeppProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build(),
                Clock.systemUTC(), Duration.ofSeconds(30));
    }
    ZeppHttpProvider(ZeppProperties properties, ObjectMapper mapper, HttpClient client, Clock clock, Duration budget) {
        this.properties = properties; this.mapper = mapper; this.client = client;
        this.clock = clock; this.budget = budget;
    }

    @Override public Submission submit(String account, String password, int steps) {
        if (!properties.ready()) throw new ApiException(ApiError.ZEPP_NOT_CONFIGURED);
        long deadline = System.nanoTime() + budget.toNanos();
        var login = new LinkedHashMap<String, String>();
        login.put("emailOrPhone", account); login.put("password", password);
        login.put("state", "REDIRECTION"); login.put("client_id", "HuaMi");
        login.put("country_code", "CN"); login.put("token", "access");
        login.put("redirect_uri", "https://s3-us-west-2.amazonaws.com/hm-registration/successsignin.html");
        byte[] encrypted = encrypt(form(login));
        login.clear();
        HttpResponse<byte[]> registration;
        try {
            registration = send(request(REGISTRATION, deadline).header("x-hm-ekv", "1")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(encrypted)).build(), deadline, false);
        } finally { Arrays.fill(encrypted, (byte) 0); }
        String access = accessCode(registration);

        var exchange = new LinkedHashMap<String, String>();
        exchange.put("app_name", APP); exchange.put("app_version", "6.14.0");
        exchange.put("code", access); exchange.put("country_code", "CN");
        exchange.put("device_id", UUID.randomUUID().toString());
        exchange.put("device_model", account.startsWith("+86") ? "phone" : "android_phone");
        exchange.put("grant_type", "access_token"); exchange.put("allow_registration", "false");
        exchange.put("third_name", account.startsWith("+86") ? "huami_phone" : "email");
        exchange.put("lang", "zh_CN"); exchange.put("source", APP + ":6.14.0:50818");
        exchange.put("os_version", "1.5.0");
        exchange.put("dn", "account.zepp.com,api-user.zepp.com,api-mifit.zepp.com,api-watch.zepp.com,app-analytics.zepp.com,api-analytics.huami.com,auth.zepp.com");
        JsonNode session = json(send(post(CLIENT_LOGIN, form(exchange), deadline), deadline, false).body());
        exchange.clear();
        if (!"ok".equals(session.path("result").asText())) throw authenticationError(session);
        String token = requiredText(session.path("token_info"), "app_token", 4096);
        String userId = session.path("token_info").path("user_id").asText();
        if (!userId.matches("[0-9]{1,20}")) throw failure();

        JsonNode bindings = json(send(request(URI.create(DATA_ORIGIN + "/v1/device/binds.json?userid=" + userId), deadline)
                .header("apptoken", token).GET().build(), deadline, false).body());
        if (bindings.has("message") && !"success".equals(bindings.path("message").asText())) throw failure();
        String boundDevice = boundDevice(bindings);
        String device = boundDevice == null ? "DA932FFFFE8816E7" : boundDevice;
        // Compute the actual upload date after login, so a login spanning midnight never writes yesterday.
        Instant now = clock.instant();
        LocalDate date = now.atZone(ZeppStepsService.ZONE).toLocalDate();
        String payload = bandData(date, device, steps);
        var upload = new LinkedHashMap<String, String>();
        upload.put("userid", userId); upload.put("last_sync_data_time", Long.toString(now.getEpochSecond()));
        upload.put("device_type", "0"); upload.put("last_deviceid", device); upload.put("data_json", payload);
        URI uri = URI.create(DATA_ORIGIN + "/v1/data/band_data.json?t=" + now.toEpochMilli() + "&r=" + UUID.randomUUID());
        var response = send(request(uri, deadline).header("apptoken", token)
                .POST(HttpRequest.BodyPublishers.ofString(form(upload), StandardCharsets.UTF_8)).build(), deadline, true);
        JsonNode result;
        try { result = json(response.body()); }
        catch (ApiException malformed) { throw new ApiException(ApiError.ZEPP_SUBMISSION_UNCERTAIN); }
        if (!"success".equals(result.path("message").asText())) throw failure();
        if (result.has("code") && (!result.path("code").isIntegralNumber()
                || !result.path("code").canConvertToInt() || result.path("code").intValue() != 0))
            throw failure();
        return new Submission(date, clock.instant(), boundDevice != null);
    }

    String bandData(LocalDate date, String device, int steps) {
        try {
            // No fabricated heart-rate, sleep, calorie or distance measurements are included.
            String summary = mapper.writeValueAsString(Map.of("v", 6, "stp", Map.of("ttl", steps), "tz", "28800"));
            return mapper.writeValueAsString(List.of(Map.of("date", date.toString(), "source", 24, "type", 0,
                    "summary", summary, "data", List.of(Map.of("start", 0, "stop", 1439,
                            "value", Base64.getEncoder().encodeToString(new byte[4320]), "tz", 32,
                            "did", device, "src", 24)))));
        } catch (Exception error) { throw failure(); }
    }

    private HttpRequest.Builder request(URI uri, long deadline) {
        return HttpRequest.newBuilder(uri).timeout(remaining(deadline))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("User-Agent", "MiFit6.14.0 (Android 12)")
                .header("app_name", APP).header("appname", APP).header("appplatform", "android_phone")
                .header("cv", "50818_6.14.0").header("v", "2.0").header("hm-privacy-ceip", "false")
                .header("x-request-id", UUID.randomUUID().toString()).header("accept-language", "zh-CN");
    }
    private HttpRequest post(URI uri, String body, long deadline) {
        return request(uri, deadline).POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
    }

    private HttpResponse<byte[]> send(HttpRequest request, long deadline, boolean upload) {
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            future = client.sendAsync(request, info -> new BoundedSubscriber());
            long nanos = deadline - System.nanoTime();
            if (nanos <= 0) throw new ApiException(upload ? ApiError.ZEPP_SUBMISSION_UNCERTAIN : ApiError.ZEPP_PROVIDER_ERROR);
            HttpResponse<byte[]> response = future.get(nanos, TimeUnit.NANOSECONDS);
            int status = response.statusCode();
            if (status == 429) throw new ApiException(ApiError.ZEPP_RATE_LIMITED);
            if (status == 401) throw new ApiException(ApiError.ZEPP_INVALID_CREDENTIALS);
            if (status == 403) throw new ApiException(ApiError.ZEPP_VERIFICATION_REQUIRED);
            if (status >= 500 && upload) throw new ApiException(ApiError.ZEPP_SUBMISSION_UNCERTAIN);
            if (status != 200 && !(request.uri().equals(REGISTRATION) && (status == 302 || status == 303)))
                throw failure();
            return response;
        } catch (ApiException error) { throw error; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApiException(upload ? ApiError.ZEPP_SUBMISSION_UNCERTAIN : ApiError.SERVICE_UNAVAILABLE);
        } catch (Exception error) {
            throw new ApiException(upload ? ApiError.ZEPP_SUBMISSION_UNCERTAIN : ApiError.ZEPP_PROVIDER_ERROR);
        } finally { if (future != null && !future.isDone()) future.cancel(true); }
    }

    private static Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw failure();
        return Duration.ofNanos(nanos);
    }

    byte[] encrypt(String body) {
        byte[] plain = body.getBytes(StandardCharsets.UTF_8);
        byte[] key = properties.loginKey().getBytes(StandardCharsets.UTF_8);
        byte[] iv = properties.loginIv().getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(plain);
        } catch (Exception error) { throw new ApiException(ApiError.ZEPP_NOT_CONFIGURED); }
        finally { Arrays.fill(plain, (byte) 0); Arrays.fill(key, (byte) 0); Arrays.fill(iv, (byte) 0); }
    }

    String accessCode(HttpResponse<byte[]> response) {
        if (response.statusCode() == 200) {
            JsonNode root = json(response.body());
            if (!root.path("access").isTextual() && !root.path("access_token").isTextual()) throw authenticationError(root);
            return requiredText(root, root.path("access").isTextual() ? "access" : "access_token", 8192);
        }
        try {
            String location = response.headers().firstValue("Location").orElse("");
            if (location.length() > 16384) throw failure();
            URI uri = URI.create(location);
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getRawQuery() == null) throw failure();
            String access = null, error = "";
            for (String pair : uri.getRawQuery().split("&")) {
                String[] parts = pair.split("=", 2);
                String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = parts.length < 2 ? "" : URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                if (name.equals("access")) {
                    if (access != null) throw failure();
                    access = value;
                } else if (name.equals("error")) error = value;
            }
            if (access == null || access.isBlank()) throw verification(error)
                    ? new ApiException(ApiError.ZEPP_VERIFICATION_REQUIRED) : new ApiException(ApiError.ZEPP_INVALID_CREDENTIALS);
            if (access.length() > 8192 || access.chars().anyMatch(Character::isISOControl)) throw failure();
            return access;
        } catch (ApiException error) { throw error; }
        catch (Exception error) { throw failure(); }
    }

    JsonNode json(byte[] bytes) {
        try {
            if (bytes.length > MAX_RESPONSE_BYTES) throw failure();
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode node = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(text);
            if (node == null || !node.isObject()) throw failure();
            return node;
        } catch (Exception error) { throw failure(); }
    }

    static String boundDevice(JsonNode bindings) {
        JsonNode items = bindings.path("items");
        if (!items.isArray() || items.size() > 100) throw failure();
        for (int pass = 0; pass < 2; pass++) for (JsonNode item : items) {
            boolean wearable = item.path("deviceType").isIntegralNumber()
                    && item.path("deviceType").canConvertToInt() && item.path("deviceType").intValue() == 0;
            String name = item.path("productName").asText().toLowerCase(Locale.ROOT);
            if (pass == 0 ? wearable : name.contains("band") || name.contains("watch") || name.contains("手环") || name.contains("手表")) {
                String id = item.path("deviceId").asText(item.path("mac").asText()).replace(":", "").toUpperCase(Locale.ROOT);
                if (id.matches("[A-Z0-9_-]{6,64}")) return id;
            }
        }
        return null;
    }
    private static String requiredText(JsonNode node, String field, int limit) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > limit
                || value.asText().chars().anyMatch(Character::isISOControl)) throw failure();
        return value.asText();
    }
    private static ApiException authenticationError(JsonNode node) {
        String hint = node.path("error").asText() + node.path("error_code").asText() + node.path("message").asText();
        return new ApiException(verification(hint) ? ApiError.ZEPP_VERIFICATION_REQUIRED : ApiError.ZEPP_INVALID_CREDENTIALS);
    }
    private static boolean verification(String hint) {
        String text = hint.toLowerCase(Locale.ROOT);
        return text.contains("captcha") || text.contains("verif") || text.contains("security") || text.contains("验证码");
    }
    static String form(Map<String, String> values) {
        return values.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }
    private static ApiException failure() { return new ApiException(ApiError.ZEPP_PROVIDER_ERROR); }

    static final class BoundedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int bytes;
        private boolean ended;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (ended) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes) {
                    ended = true; subscription.cancel(); delegate.onError(new IllegalStateException("Response budget exceeded")); return;
                }
                bytes += buffer.remaining();
            }
            delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { if (!ended) { ended = true; delegate.onError(error); } }
        @Override public void onComplete() { if (!ended) { ended = true; delegate.onComplete(); } }
    }
}
