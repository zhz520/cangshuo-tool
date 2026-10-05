package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminAnnouncementRequest;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AdminAnnouncementInput {
    private static final Set<String> LEVELS = Set.of("INFO", "WARNING", "CRITICAL");

    public record Normalized(String title, String body, String level, boolean published, Instant startAt,
        Instant endAt) { }

    public Normalized normalize(AdminAnnouncementRequest request) {
        String title = singleLine(request.title(), 128);
        String body = multiLine(request.body(), 2000);
        String level = request.level() == null ? "INFO" : request.level().strip().toUpperCase(Locale.ROOT);
        if (!LEVELS.contains(level)) invalid();
        boolean published = !Boolean.FALSE.equals(request.enabled());
        Instant startAt = instant(request.startAt());
        Instant endAt = instant(request.endAt());
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) invalid();
        return new Normalized(title, body, level, published, startAt, endAt);
    }

    public long id(String rawId) {
        try {
            long value = Long.parseLong(rawId);
            if (value <= 0) throw new ApiException(ApiError.INVALID_ARGUMENT);
            return value;
        } catch (NumberFormatException exception) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
    }

    private static String singleLine(String value, int max) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > max
                || normalized.chars().anyMatch(Character::isISOControl)) invalid();
        return normalized;
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

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException exception) {
            invalid();
            return null;
        }
    }

    private static void invalid() { throw new ApiException(ApiError.INVALID_ARGUMENT); }
}
