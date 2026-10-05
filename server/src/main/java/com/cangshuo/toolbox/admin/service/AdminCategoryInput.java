package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminCategoryRequest;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AdminCategoryInput {
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");
    private static final Pattern ICON = Pattern.compile("^[a-z][a-z0-9_]{0,127}$");

    public record Normalized(String code, String name, String icon, String description, int sortOrder,
        boolean enabled) { }

    public String code(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) invalid();
        return normalized;
    }

    public Normalized normalize(AdminCategoryRequest request, boolean requireCode) {
        String code = requireCode ? code(request.code()) : null;
        String name = text(request.name(), 64, true);
        String description = request.description() == null ? "" : text(request.description(), 500, false);
        String icon = request.icon() == null || request.icon().isBlank() ? null : request.icon().strip();
        if (icon != null && !ICON.matcher(icon).matches()) invalid();
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        if (sortOrder < 0 || sortOrder > 100_000) invalid();
        boolean enabled = !Boolean.FALSE.equals(request.enabled());
        return new Normalized(code, name, icon, description, sortOrder, enabled);
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
