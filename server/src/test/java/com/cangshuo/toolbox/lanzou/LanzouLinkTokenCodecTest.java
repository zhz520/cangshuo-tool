package com.cangshuo.toolbox.lanzou;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanzouLinkTokenCodecTest {
    private final LanzouLinkTokenCodec codec = new LanzouLinkTokenCodec("correct-horse-battery-staple-32-plus");
    private final LanzouInput input = new LanzouInput(URI.create("https://www.lanzouf.com/iAbC123?source=share"), "p@ss_1-中");

    @Test void encryptedCapabilityRoundTripsWithoutExposingShareOrPassword() {
        String token = codec.encode(input, Instant.parse("2026-10-22T01:02:03Z"));
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
        assertFalse(token.contains("lanzouf")); assertFalse(token.contains("p@ss"));
        var decoded = codec.decode(token);
        assertNotNull(decoded); assertEquals(input, decoded.input());
        assertEquals(Instant.parse("2026-10-22T01:02:03Z"), decoded.createdAt());
    }
    @Test void tamperedTruncatedAndWrongKeyTokensFailClosed() {
        String token = codec.encode(input, Instant.parse("2026-10-22T01:02:03Z"));
        char replacement = token.charAt(token.length() / 2) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, token.length() / 2) + replacement + token.substring(token.length() / 2 + 1);
        assertNull(codec.decode(tampered)); assertNull(codec.decode(token.substring(0, 20)));
        assertNull(codec.decode("!" + token)); assertNull(codec.decode(null));
        assertNull(new LanzouLinkTokenCodec("different-secret-that-is-long-enough").decode(token));
    }
    @Test void weakOrMissingSecretsDisableTheCapability() {
        for (String secret : new String[]{null, "", "short"}) {
            var disabled = new LanzouLinkTokenCodec(secret);
            assertFalse(disabled.available()); assertNull(disabled.decode("A".repeat(40)));
            assertThrows(IllegalStateException.class,
                    () -> disabled.encode(input, Instant.parse("2026-10-22T01:02:03Z")));
        }
    }
}
