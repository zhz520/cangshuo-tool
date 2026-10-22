package com.cangshuo.toolbox.lanzou;

import com.cangshuo.toolbox.common.exception.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

record LanzouInput(URI uri, String password) {
    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\\\"'`]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD = Pattern.compile(
            "(?i)(?:分享密码|提取码|访问码|password|pwd)\\s*[:：=]?\\s*([A-Za-z0-9_-]{1,64})");

    static LanzouInput parse(String text, String suppliedPassword) {
        if (text == null || text.isBlank() || text.length() > 4096 || LanzouHosts.hasControl(text))
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        Matcher matcher = LINK.matcher(text);
        if (!matcher.find()) throw new ApiException(ApiError.INVALID_ARGUMENT);
        String link = matcher.group().replaceAll("[。！，、；：）】〉》\\]}),.;!]+$", "");
        if (matcher.find()) throw new ApiException(ApiError.LANZOU_LINK_INVALID);
        try {
            URI uri = URI.create(link);
            if ("http".equalsIgnoreCase(uri.getScheme())) uri = URI.create("https:" + link.substring(5));
            uri = LanzouHosts.page(uri);
            String password = clean(suppliedPassword);
            String fromLink = queryPassword(uri);
            uri = removePassword(uri);
            if (password.isEmpty()) password = fromLink;
            if (password.isEmpty()) {
                Matcher found = PASSWORD.matcher(text);
                if (found.find()) password = clean(found.group(1));
            }
            return new LanzouInput(uri, password);
        } catch (ApiException error) {
            throw error;
        } catch (IllegalArgumentException error) {
            throw new ApiException(ApiError.LANZOU_LINK_INVALID);
        }
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return "";
        String clean = value.strip();
        if (clean.length() > 64 || clean.codePoints().anyMatch(Character::isISOControl))
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        return clean;
    }

    private static String queryPassword(URI uri) {
        String query = uri.getRawQuery();
        if (query == null) return "";
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals < 1) continue;
            try {
                String key = URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8);
                if (Set.of("pwd", "password", "code").contains(key.toLowerCase(Locale.ROOT)))
                    return clean(URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) { return ""; }
        }
        return "";
    }

    private static URI removePassword(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) return uri;
        List<String> kept = new ArrayList<>();
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            try { key = URLDecoder.decode(key, StandardCharsets.UTF_8); }
            catch (IllegalArgumentException ignored) {}
            if (!Set.of("pwd", "password", "code").contains(key.toLowerCase(Locale.ROOT)) && !pair.isEmpty())
                kept.add(pair);
        }
        try {
            String queryValue = kept.isEmpty() ? "" : "?" + String.join("&", kept);
            return URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath() + queryValue);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ApiError.LANZOU_LINK_INVALID);
        }
    }
}
