package com.cangshuo.toolbox.auth.service;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AuthInput {
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
            + "[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");
    private AuthInput() { }

    public static String email(String value) {
        if (value == null) invalid();
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 128 || !EMAIL.matcher(normalized).matches()
                || normalized.indexOf('@') > 64) invalid();
        return normalized;
    }

    public static void password(String value) {
        if (value == null || value.length() < 8 || value.length() > 72
                || value.getBytes(StandardCharsets.UTF_8).length > 72
                || value.chars().anyMatch(Character::isISOControl) || !validUnicode(value)) invalid();
    }

    public static String nickname(String value) {
        if (value == null) invalid();
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 64 || !validUnicode(normalized)
                || normalized.chars().anyMatch(Character::isISOControl)) invalid();
        return normalized;
    }

    private static boolean validUnicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }

    private static void invalid() { throw new ApiException(ApiError.INVALID_ARGUMENT); }
}
