package com.cangshuo.toolbox.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Bounded magic-byte sniffing for the supported upload types. The declared content type must match what
 * the bytes actually are, so a renamed or mislabelled payload never reaches object storage.
 */
public final class FileSignatures {
    public static final int HEADER_BYTES = 32;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private FileSignatures() { }

    /** Returns the detected MIME type, or null when the header matches no supported type. */
    public static String detect(byte[] header) {
        if (header == null || header.length == 0) return null;
        if (startsWith(header, PNG)) return "image/png";
        if (startsWith(header, JPEG)) return "image/jpeg";
        if (header.length >= 12 && startsWith(header, RIFF) && matchesAt(header, 8, WEBP)) return "image/webp";
        if (startsWith(header, PDF)) return "application/pdf";
        if (looksLikeText(header)) return "text/plain";
        return null;
    }

    /** True when the declared type is exactly the detected type; either being unknown fails. */
    public static boolean matches(String declaredType, String detectedType) {
        return declaredType != null && declaredType.equals(detectedType);
    }

    private static boolean looksLikeText(byte[] header) {
        for (byte value : header) {
            int unsigned = value & 0xFF;
            if (unsigned == 0) return false;
            boolean allowedControl = unsigned == '\t' || unsigned == '\n' || unsigned == '\r' || unsigned == 0x0C;
            if (unsigned < 0x20 && !allowedControl) return false;
        }
        return true;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        return value.length >= prefix.length && matchesAt(value, 0, prefix);
    }

    private static boolean matchesAt(byte[] value, int offset, byte[] expected) {
        return value.length >= offset + expected.length
                && Arrays.equals(value, offset, offset + expected.length, expected, 0, expected.length);
    }
}
