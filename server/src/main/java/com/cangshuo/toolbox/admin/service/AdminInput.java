package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AdminInput {
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,31}$");
    private static final Pattern ROLE = Pattern.compile("^[A-Z][A-Z0-9_]{2,31}$");
    private AdminInput() { }

    public static String username(String value) {
        if (value == null) invalid();
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(normalized).matches()) invalid();
        return normalized;
    }

    public static void password(String value) {
        if (value == null || value.length() < 8 || value.length() > 72
                || value.getBytes(StandardCharsets.UTF_8).length > 72
                || value.chars().anyMatch(Character::isISOControl)) invalid();
    }

    public static void bootstrapPassword(String value) {
        if (value == null || value.length() < 12 || value.length() > 72
                || value.getBytes(StandardCharsets.UTF_8).length > 72
                || value.chars().anyMatch(Character::isISOControl)
                || value.chars().noneMatch(Character::isLetter)
                || value.chars().noneMatch(Character::isDigit)) invalid();
    }

    public static String nickname(String value) {
        if (value == null) invalid();
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 32
                || normalized.chars().anyMatch(Character::isISOControl)) invalid();
        return normalized;
    }

    public static String role(String value) {
        if (value == null || !ROLE.matcher(value).matches()) invalid();
        return value;
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String candidate = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].strip();
        if (candidate == null || candidate.isBlank()
                || candidate.chars().anyMatch(c -> Character.isISOControl(c))) candidate = "unknown";
        return candidate.length() > 45 ? candidate.substring(0, 45) : candidate;
    }

    private static void invalid() { throw new ApiException(ApiError.INVALID_ARGUMENT); }
}
