package com.cangshuo.toolbox.storage;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Server-generated object keys; client input is only ever matched against a strict pattern. */
public final class StorageKeys {
    private static final Pattern KEY = Pattern.compile("^[a-z0-9][a-z0-9._/-]{0,191}$");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/webp", "webp", "application/pdf", "pdf",
            "text/plain", "txt");

    private StorageKeys() { }

    public static String generate(Clock clock, String contentType) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String extension = EXTENSIONS.getOrDefault(normalizeContentType(contentType), "bin");
        return String.format(Locale.ROOT, "objects/%04d%02d/%s.%s", today.getYear(), today.getMonthValue(),
                UUID.randomUUID().toString().replace("-", ""), extension);
    }

    public static String requireValid(String key) {
        String normalized = key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
        if (!KEY.matcher(normalized).matches() || normalized.contains("..") || normalized.contains("//")
                || normalized.endsWith(".") || normalized.endsWith("/")) {
            throw new IllegalArgumentException("Invalid object key");
        }
        return normalized;
    }

    public static String normalizeContentType(String contentType) {
        if (contentType == null) return "";
        int separator = contentType.indexOf(';');
        String value = separator < 0 ? contentType : contentType.substring(0, separator);
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
