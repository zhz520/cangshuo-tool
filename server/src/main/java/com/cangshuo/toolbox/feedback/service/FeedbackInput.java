package com.cangshuo.toolbox.feedback.service;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FeedbackInput {
    public static final Set<String> TYPES = Set.of("BUG", "SUGGESTION", "OTHER");

    public record Normalized(String type, String content, String contact) { }

    public Normalized normalize(String rawType, String rawContent, String rawContact) {
        String type = rawType == null ? "" : rawType.strip().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) invalid();
        String content = multiLine(rawContent, 2000);
        String contact = singleLine(rawContact, 128);
        return new Normalized(type, content, contact);
    }

    private static String multiLine(String value, int max) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > max) invalid();
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '\n') continue;
            if (Character.isISOControl(character)) invalid();
        }
        return normalized;
    }

    private static String singleLine(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > max || normalized.chars().anyMatch(Character::isISOControl)) invalid();
        return normalized;
    }

    private static void invalid() { throw new ApiException(ApiError.INVALID_ARGUMENT); }
}
