package com.cangshuo.toolbox.ai;

import com.cangshuo.toolbox.common.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenAiCompatibleProviderTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final OpenAiCompatibleProvider provider=new OpenAiCompatibleProvider(AiServiceTest.properties(true),mapper);
    private byte[] bytes(String text) {return text.getBytes(StandardCharsets.UTF_8);}
    @Test void parsesTextLengthAndUsage() throws Exception {
        var response=provider.parse(bytes("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"结果\"}}],\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":2}}"));
        assertEquals("结果",response.text());assertTrue(response.truncated());assertEquals(4L,response.inputTokens());
    }
    @Test void refusesEmptyMalformedToolCallAndOversizedResponses() {
        for(var content:List.of("{}","{\"choices\":[]}","{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"content\":\"text\"}}]}"))
            assertThrows(ApiException.class,()->provider.parse(bytes(content)));
        assertThrows(ApiException.class,()->provider.parse(new byte[262145]));
    }
    @Test void responseBudgetCancelsPublisherBeforeAccumulation() {
        var subscriber=new OpenAiCompatibleProvider.BoundedBodySubscriber();var subscription=mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);subscriber.onNext(List.of(ByteBuffer.wrap(new byte[262145])));
        assertThrows(CompletionException.class,()->subscriber.getBody().toCompletableFuture().join());verify(subscription).cancel();
    }
    @Test void rejectsRefusalToolCallsTrailingJsonAndInvalidUtf8() {
        String prefix="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"text\"";
        for(String extra:List.of(",\"refusal\":\"declined\"",",\"tool_calls\":[{}]",",\"function_call\":{}"))
            assertThrows(ApiException.class,()->provider.parse(bytes(prefix+extra+"}}]}")));
        assertThrows(Exception.class,()->provider.parse(bytes(prefix+"}}]} {}")));
        assertThrows(Exception.class,()->provider.parse(new byte[]{(byte)0xc3,(byte)0x28}));
    }
    @Test @SuppressWarnings("unchecked") void redirectsAndProviderErrorsAreNotRetried() {
        var client=mock(HttpClient.class);HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(302);
        when(client.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(response));
        var transport=new OpenAiCompatibleProvider(AiServiceTest.properties(true),mapper,client);
        assertEquals(ApiError.AI_PROVIDER_ERROR,assertThrows(ApiException.class,()->transport.complete("instruction","secret-input")).error());
        verify(client,times(1)).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test @SuppressWarnings("unchecked") void timeoutCancelsProviderFuture() {
        var client=mock(HttpClient.class);var pending=new CompletableFuture<HttpResponse<byte[]>>();
        when(client.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        var transport=new OpenAiCompatibleProvider(AiServiceTest.properties(true),mapper,client);
        assertThrows(ApiException.class,()->transport.complete("instruction","input"));assertTrue(pending.isCancelled());
    }
}
