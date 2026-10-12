package com.cangshuo.toolbox.zepp;

import com.cangshuo.toolbox.common.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ZeppHttpProviderTest {
    static ZeppProperties properties() { return new ZeppProperties(true,"test-key-16bytes","test--iv-16bytes"); }
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T16:00:01Z"), ZoneOffset.UTC);
    private ZeppHttpProvider provider(HttpClient client) { return new ZeppHttpProvider(properties(),mapper,client,clock,Duration.ofSeconds(2)); }
    @SuppressWarnings("unchecked") private HttpResponse<byte[]> response(int status, String body, String location) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(response.headers()).thenReturn(HttpHeaders.of(location == null ? Map.of() : Map.of("Location", List.of(location)),(a,b)->true));
        return response;
    }
    private static byte[] requestBody(HttpRequest request) throws Exception {
        var body = new java.io.ByteArrayOutputStream();
        var complete = new CompletableFuture<byte[]>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer b) { byte[] value = new byte[b.remaining()]; b.get(value); body.writeBytes(value); }
            public void onError(Throwable e) { complete.completeExceptionally(e); }
            public void onComplete() { complete.complete(body.toByteArray()); }
        });
        return complete.get(2, TimeUnit.SECONDS);
    }
    private static Map<String,String> decode(String body) {
        Map<String,String> fields = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=",2);
            fields.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8));
        }
        return fields;
    }

    @Test @SuppressWarnings("unchecked") void completePipelineEncodesSecretsUsesBoundDeviceAndBeijingUploadDate() throws Exception {
        var client = mock(HttpClient.class);
        List<HttpRequest> sent = new ArrayList<>();
        List<byte[]> bodies = new ArrayList<>();
        var replies = new ArrayDeque<>(List.of(
                response(303,"","https://s3-us-west-2.amazonaws.com/hm-registration/successsignin.html?region=cn&access=A%2BB%26C"),
                response(200,"{\"result\":\"ok\",\"token_info\":{\"app_token\":\"test-app-token\",\"user_id\":123}}",null),
                response(200,"{\"items\":[{\"deviceType\":0,\"mac\":\"aa:bb:cc:dd:ee:ff\"}]}",null),
                response(200,"{\"code\":0,\"message\":\"success\"}",null)));
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0); sent.add(request);
            bodies.add(request.bodyPublisher().isPresent() ? requestBody(request) : new byte[0]);
            return CompletableFuture.completedFuture(replies.remove());
        });
        var result = provider(client).submit("test@example.com"," a+b&%=中 ",12345);
        assertEquals(LocalDate.of(2026,10,13),result.date()); assertTrue(result.boundDevice()); assertEquals(4,sent.size());
        assertEquals(ZeppHttpProvider.REGISTRATION,sent.get(0).uri());
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(properties().loginKey().getBytes(StandardCharsets.UTF_8),"AES"),
                new IvParameterSpec(properties().loginIv().getBytes(StandardCharsets.UTF_8)));
        Map<String,String> login = decode(new String(cipher.doFinal(bodies.get(0)),StandardCharsets.UTF_8));
        assertEquals(" a+b&%=中 ",login.get("password"));
        assertEquals("test@example.com",login.get("emailOrPhone"));
        assertEquals("A+B&C",decode(new String(bodies.get(1),StandardCharsets.UTF_8)).get("code"));
        assertEquals("email",decode(new String(bodies.get(1),StandardCharsets.UTF_8)).get("third_name"));
        var upload = decode(new String(bodies.get(3),StandardCharsets.UTF_8));
        assertEquals("AABBCCDDEEFF",upload.get("last_deviceid"));
        var day = mapper.readTree(upload.get("data_json")).get(0);
        assertEquals("2026-10-13",day.path("date").asText());
        assertEquals(12345,mapper.readTree(day.path("summary").asText()).path("stp").path("ttl").asInt());
        assertFalse(day.has("data_hr")); assertFalse(mapper.readTree(day.path("summary").asText()).has("slp"));
        assertEquals(4320,Base64.getDecoder().decode(day.path("data").get(0).path("value").asText()).length);
        for (HttpRequest req : sent) { assertFalse(req.uri().toString().contains("test-app-token")); assertFalse(req.uri().toString().contains("test@example.com")); }
    }

    @Test void accessCodeHandlesLastQueryParameterAndNeverFollowsRedirects() {
        var provider = provider(mock(HttpClient.class));
        assertEquals("a+b",provider.accessCode(response(303,"","https://example.invalid/success?access=a%2Bb")));
        assertEquals(ApiError.ZEPP_PROVIDER_ERROR,assertThrows(ApiException.class,()->provider.accessCode(response(303,"","http://example.invalid/?access=a"))).error());
        assertThrows(ApiException.class,()->provider.accessCode(response(303,"","https://example.invalid/?access=a&access=b")));
    }
    @Test void classifiesCaptchaAndRejectedCredentialsWithoutEchoingProviderDetails() {
        var provider = provider(mock(HttpClient.class));
        assertEquals(ApiError.ZEPP_VERIFICATION_REQUIRED,assertThrows(ApiException.class,
                ()->provider.accessCode(response(303,"","https://example.invalid/?error=captcha_required"))).error());
        var error = assertThrows(ApiException.class,()->provider.accessCode(response(200,"{\"error\":\"bad_password test-secret\"}",null)));
        assertEquals(ApiError.ZEPP_INVALID_CREDENTIALS,error.error()); assertFalse(error.getMessage().contains("test-secret"));
    }
    @Test void strictJsonRejectsMalformedDuplicateAndOversizedBodies() {
        var provider = provider(mock(HttpClient.class));
        for (String raw : List.of("[]","{} {}","{\"message\":\"success\",\"message\":\"fail\"}"))
            assertThrows(ApiException.class,()->provider.json(raw.getBytes(StandardCharsets.UTF_8)));
        assertThrows(ApiException.class,()->provider.json(new byte[]{(byte)0xc3,(byte)0x28}));
        assertThrows(ApiException.class,()->provider.json(new byte[65537]));
    }
    @Test void boundedSubscriberCancelsBeforeAllocatingAnOversizedChunk() {
        var subscriber = new ZeppHttpProvider.BoundedSubscriber(); var subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription); subscriber.onNext(List.of(ByteBuffer.wrap(new byte[65537])));
        assertThrows(CompletionException.class,()->subscriber.getBody().toCompletableFuture().join()); verify(subscription).cancel();
    }
    @Test @SuppressWarnings("unchecked") void upstreamRateLimitStopsWithoutRetry() {
        var client = mock(HttpClient.class);
        var limited = CompletableFuture.completedFuture(response(429,"",null));
        when(client.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenReturn(limited);
        assertEquals(ApiError.ZEPP_RATE_LIMITED,assertThrows(ApiException.class,()->provider(client).submit("a@example.com","password",10000)).error());
        verify(client,times(1)).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test @SuppressWarnings("unchecked") void uploadTimeoutCancelsFutureAndReportsUncertainRatherThanSuccess() {
        var client = mock(HttpClient.class); var pending = new CompletableFuture<HttpResponse<byte[]>>();
        var registration = CompletableFuture.completedFuture(response(200,"{\"access\":\"abc\"}",null));
        var session = CompletableFuture.completedFuture(response(200,"{\"result\":\"ok\",\"token_info\":{\"app_token\":\"token\",\"user_id\":\"123\"}}",null));
        var devices = CompletableFuture.completedFuture(response(200,"{\"items\":[]}",null));
        when(client.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(
                registration, session, devices, pending);
        var provider = new ZeppHttpProvider(properties(),mapper,client,clock,Duration.ofMillis(100));
        assertEquals(ApiError.ZEPP_SUBMISSION_UNCERTAIN,assertThrows(ApiException.class,()->provider.submit("a@example.com","password",10000)).error());
        assertTrue(pending.isCancelled()); verify(client,times(4)).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test void deviceLookupPrefersWearablesAndRejectsUnexpectedShapes() throws Exception {
        assertEquals("ABCDEF123456",ZeppHttpProvider.boundDevice(mapper.readTree("{\"items\":[{\"deviceType\":1,\"deviceId\":\"ignored1\"},{\"deviceType\":0,\"deviceId\":\"abcdef123456\"}]}")));
        assertNull(ZeppHttpProvider.boundDevice(mapper.readTree("{\"items\":[]}")));
        assertNull(ZeppHttpProvider.boundDevice(mapper.readTree("{\"message\":\"success\"}")));
        assertThrows(ApiException.class,()->ZeppHttpProvider.boundDevice(mapper.readTree("{\"items\":\"invalid\"}")));
    }
    @Test void uploadAcceptanceAcceptsNumericOrTextualZeroAndRejectsErrors() throws Exception {
        assertTrue(ZeppHttpProvider.uploadAccepted(mapper.readTree("{\"message\":\"success\",\"code\":0}")));
        assertTrue(ZeppHttpProvider.uploadAccepted(mapper.readTree("{\"message\":\"success\",\"code\":1}")));
        assertTrue(ZeppHttpProvider.uploadAccepted(mapper.readTree("{\"message\":\"success\"}")));
        assertTrue(ZeppHttpProvider.uploadAccepted(mapper.readTree("{\"message\":\"success\",\"code\":\"1\"}")));
        assertFalse(ZeppHttpProvider.uploadAccepted(mapper.readTree("{\"message\":\"fail\",\"code\":0}")));
        assertFalse(ZeppHttpProvider.uploadAccepted(mapper.readTree("{\"code\":0}")));
        assertFalse(ZeppHttpProvider.uploadAccepted(mapper.readTree("{\"message\":\"Success\"}")));
    }
    @Test void diagnosticDetailIsBoundedAndAsciiOnly() {
        assertEquals("ok-123",ZeppHttpProvider.safe("ok-\u0007\u4e2d123"));
        assertTrue(ZeppHttpProvider.safe("x".repeat(200)).length() <= 40);
        assertEquals("",ZeppHttpProvider.safe(null));
    }
    @Test @SuppressWarnings("unchecked") void bindingLookupFailureStillUploadsWithFallbackDevice() throws Exception {
        var client = mock(HttpClient.class);
        List<byte[]> bodies = new ArrayList<>();
        var replies = new ArrayDeque<>(List.of(
                response(303,"","https://s3-us-west-2.amazonaws.com/hm-registration/successsignin.html?access=abc"),
                response(200,"{\"result\":\"ok\",\"token_info\":{\"app_token\":\"tk\",\"user_id\":123}}",null),
                response(500,"",null),
                response(200,"{\"message\":\"success\",\"code\":0}",null)));
        when(client.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            bodies.add(request.bodyPublisher().isPresent() ? requestBody(request) : new byte[0]);
            return CompletableFuture.completedFuture(replies.remove());
        });
        var result = provider(client).submit("test@example.com","password",10000);
        assertFalse(result.boundDevice());
        assertEquals("DA932FFFFE8816E7",decode(new String(bodies.get(3),StandardCharsets.UTF_8)).get("last_deviceid"));
    }
}
