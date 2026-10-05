package com.cangshuo.toolbox.storage;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FileSignaturesTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    @Test void detectsSupportedSignatures() {
        assertEquals("image/png", FileSignatures.detect(PNG));
        assertEquals("image/jpeg", FileSignatures.detect(JPEG));
        assertEquals("image/webp", FileSignatures.detect(WEBP));
        assertEquals("application/pdf", FileSignatures.detect(PDF));
        assertEquals("text/plain", FileSignatures.detect("hello 世界\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals("text/plain", FileSignatures.detect("\t\r\n".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test void rejectsBinaryAndUnknownPayloads() {
        assertNull(FileSignatures.detect(null));
        assertNull(FileSignatures.detect(new byte[0]));
        assertNull(FileSignatures.detect(new byte[]{0, 1, 2, 3}));
        assertNull(FileSignatures.detect(new byte[]{0x01, 0x02, 0x03}));
        assertNull(FileSignatures.detect(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '}));
    }

    @Test void declaredTypeMustMatchTheDetectedType() {
        assertTrue(FileSignatures.matches("image/png", "image/png"));
        assertTrue(FileSignatures.matches("text/plain", "text/plain"));
        assertFalse(FileSignatures.matches("application/pdf", "image/png"));
        assertFalse(FileSignatures.matches("image/png", null));
        assertFalse(FileSignatures.matches(null, "image/png"));
        assertFalse(FileSignatures.matches(null, null));
    }
}
