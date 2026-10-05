package com.cangshuo.toolbox.ai;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.stereotype.Component;

/** One bounded, non-streaming provider request. No logging of prompts, secrets or provider errors. */
@Component
public class OpenAiCompatibleProvider implements AiProvider {
    static final int MAX_RESPONSE_BYTES = 262_144;
    private final AiProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiCompatibleProvider(AiProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    OpenAiCompatibleProvider(AiProperties properties, ObjectMapper mapper, HttpClient client) {
        this.properties = properties; this.mapper = mapper; this.client = client;
    }
    @Override public AiResponse complete(String instruction, String input) {
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of("model", properties.model(), "stream", false,
                properties.tokenParameter(), properties.maxOutputTokens(), "messages", List.of(
                    Map.of("role", "system", "content", instruction), Map.of("role", "user", "content", input))));
            var request = HttpRequest.newBuilder(URI.create(properties.endpoint()))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds())).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.apiKey()).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            future = client.sendAsync(request, info -> new BoundedBodySubscriber());
            var response = future.get(properties.timeoutSeconds(), TimeUnit.SECONDS);
            if (response.statusCode() == 429) throw new ApiException(ApiError.TOO_MANY_REQUESTS);
            if (response.statusCode() != 200) throw new ApiException(ApiError.AI_PROVIDER_ERROR);
            return parse(response.body());
        } catch (ApiException error) { throw error; }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new ApiException(ApiError.SERVICE_UNAVAILABLE); }
        catch (Exception error) { throw new ApiException(ApiError.AI_PROVIDER_ERROR); }
        finally { if (future != null && !future.isDone()) future.cancel(true); }
    }
    AiResponse parse(byte[] bytes) throws Exception {
        if (bytes.length > MAX_RESPONSE_BYTES) throw new ApiException(ApiError.AI_PROVIDER_ERROR);
        String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        JsonNode root = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .readTree(json);
        if (root == null || !root.isObject()) throw new ApiException(ApiError.AI_PROVIDER_ERROR);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() != 1) throw new ApiException(ApiError.AI_PROVIDER_ERROR);
        JsonNode choice = choices.get(0), message = choice.path("message"), content = message.path("content");
        String reason = choice.path("finish_reason").asText();
        if (!content.isTextual() || content.asText().isBlank() || content.asText().length() > 16000 ||
            !List.of("stop", "length").contains(reason) || !message.path("role").asText().equals("assistant") ||
            (!message.path("refusal").isMissingNode() && !message.path("refusal").isNull()) ||
            (!message.path("tool_calls").isMissingNode() && !message.path("tool_calls").isNull() &&
                (!message.path("tool_calls").isArray() || !message.path("tool_calls").isEmpty())) ||
            (!message.path("function_call").isMissingNode() && !message.path("function_call").isNull()))
            throw new ApiException(ApiError.AI_PROVIDER_ERROR);
        return new AiResponse(content.asText(), properties.model(), reason.equals("length"),
            tokens(root.path("usage").path("prompt_tokens")), tokens(root.path("usage").path("completion_tokens")));
    }
    private static Long tokens(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) throw new ApiException(ApiError.AI_PROVIDER_ERROR);
        return node.longValue();
    }
    static class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long bytes;
        private boolean ended;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (ended) return;
            for (var buffer : buffers) bytes += buffer.remaining();
            if (bytes > MAX_RESPONSE_BYTES) { ended = true; subscription.cancel(); delegate.onError(new IllegalStateException("AI response exceeds budget")); }
            else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { if (!ended) { ended = true; delegate.onError(error); } }
        @Override public void onComplete() { if (!ended) { ended = true; delegate.onComplete(); } }
    }
}
