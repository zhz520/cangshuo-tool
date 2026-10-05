package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminRecommendationRequest;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AdminRecommendationInput {
    private static final Pattern SLOT = Pattern.compile("^[a-z][a-z0-9_]{0,31}$");
    private static final Pattern TOOL = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    public record Normalized(String slotCode, String title, String subtitle, String toolCode, String linkUrl,
        String imageUrl, int sortOrder, boolean enabled, Instant startAt, Instant endAt) { }

    public String slotCode(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!SLOT.matcher(normalized).matches()) invalid();
        return normalized;
    }

    public Normalized normalize(AdminRecommendationRequest request, boolean requireCode) {
        String slotCode = requireCode ? slotCode(request.slotCode()) : null;
        String title = text(request.title(), 64, true);
        String subtitle = request.subtitle() == null ? "" : text(request.subtitle(), 128, false);
        String toolCode = request.toolCode() == null || request.toolCode().isBlank() ? null
                : request.toolCode().strip();
        if (toolCode != null && !TOOL.matcher(toolCode).matches()) invalid();
        String linkUrl = httpsUrl(request.linkUrl(), 500);
        String imageUrl = httpsUrl(request.imageUrl(), 500);
        if ((toolCode == null) == (linkUrl == null)) invalid();
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        if (sortOrder < 0 || sortOrder > 100_000) invalid();
        boolean enabled = !Boolean.FALSE.equals(request.enabled());
        Instant startAt = instant(request.startAt());
        Instant endAt = instant(request.endAt());
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) invalid();
        return new Normalized(slotCode, title, subtitle, toolCode, linkUrl, imageUrl, sortOrder, enabled, startAt, endAt);
    }

    private static String httpsUrl(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > max || !normalized.startsWith("https://")) invalid();
        try {
            URI uri = URI.create(normalized);
            if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) invalid();
        } catch (IllegalArgumentException exception) {
            invalid();
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

    private static String text(String value, int max, boolean required) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) {
            if (required) invalid();
            return "";
        }
        if (normalized.length() > max || normalized.chars().anyMatch(Character::isISOControl)) invalid();
        return normalized;
    }

    private static void invalid() { throw new ApiException(ApiError.INVALID_ARGUMENT); }
}
