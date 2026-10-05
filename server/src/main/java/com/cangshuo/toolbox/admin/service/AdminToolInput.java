package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminToolRequest;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Normalizes and validates administrator tool payloads before they reach SQL. */
@Component
public class AdminToolInput {
    public static final int MAX_CONFIG_CHARS = 10_000;
    public static final int MAX_KEYWORDS = 20;
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private static final Pattern CATEGORY = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");
    private static final Pattern ICON = Pattern.compile("^[a-z][a-z0-9_]{0,127}$");
    private static final Set<String> MODES = Set.of("LOCAL", "SERVER", "HYBRID", "WEB");
    private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED", "MAINTENANCE");

    private final ObjectMapper mapper;
    public AdminToolInput(ObjectMapper mapper) { this.mapper = mapper; }

    public record Normalized(String code, String name, String description, String categoryCode, String icon,
        String keywordsJson, String mode, boolean requiresLogin, String status, int version, int sortOrder,
        boolean featured, String configJson) { }

    public String code(String value) {
        String normalized = value == null ? "" : value.strip();
        if (!CODE.matcher(normalized).matches()) invalid();
        return normalized;
    }

    public Normalized normalize(AdminToolRequest request, boolean requireCode) {
        String code = requireCode ? code(request.code()) : null;
        String name = text(request.name(), 128, true);
        String description = request.description() == null ? "" : text(request.description(), 500, false);
        String categoryCode = request.categoryCode() == null ? "" : request.categoryCode().strip();
        if (!CATEGORY.matcher(categoryCode).matches()) invalid();
        String icon = request.icon() == null || request.icon().isBlank() ? null : request.icon().strip();
        if (icon != null && !ICON.matcher(icon).matches()) invalid();
        String keywordsJson = keywords(request.keywords());
        String mode = request.mode() == null ? "" : request.mode().strip().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) invalid();
        String status = request.status() == null ? "ENABLED" : request.status().strip().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) invalid();
        int version = request.version() == null ? 1 : request.version();
        if (version < 1 || version > 100_000) invalid();
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        if (sortOrder < 0 || sortOrder > 100_000) invalid();
        boolean requiresLogin = Boolean.TRUE.equals(request.requiresLogin());
        boolean featured = Boolean.TRUE.equals(request.featured());
        String configJson = config(request.configJson());
        return new Normalized(code, name, description, categoryCode, icon, keywordsJson, mode, requiresLogin,
                status, version, sortOrder, featured, configJson);
    }

    public String status(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) invalid();
        return normalized;
    }

    private String keywords(List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        if (values.size() > MAX_KEYWORDS) invalid();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String keyword = text(value, 32, false);
            unique.add(keyword);
        }
        try {
            return mapper.writeValueAsString(unique);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Keyword serialization failed");
        }
    }

    private String config(String value) {
        String raw = value == null || value.isBlank() ? "{}" : value.strip();
        if (raw.length() > MAX_CONFIG_CHARS) invalid();
        JsonNode node;
        try {
            node = mapper.readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            invalid();
            return "{}";
        }
        if (node == null || !node.isObject()) invalid();
        try {
            return mapper.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Configuration serialization failed");
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
