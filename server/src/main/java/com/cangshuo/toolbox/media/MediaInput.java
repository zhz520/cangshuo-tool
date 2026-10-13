package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import java.net.URI;
import java.util.regex.*;

public record MediaInput(URI uri, MediaPlatform platform) {
    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\\\"'`]+", Pattern.CASE_INSENSITIVE);
    public static MediaInput parse(String text) {
        if (text == null || text.isBlank() || text.length() > 4096
                || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != 10 && c != 13 && c != 9))
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        Matcher matcher = LINK.matcher(text);
        if (!matcher.find()) throw new ApiException(ApiError.INVALID_ARGUMENT);
        String link = matcher.group().replaceAll("[。！，、；：）】〉》\\]}).,;!]+$", "");
        if (matcher.find()) throw new ApiException(ApiError.INVALID_ARGUMENT);
        try {
            URI uri = URI.create(link);
            if (uri.getRawFragment() != null) uri = URI.create(link.substring(0, link.indexOf('#')));
            // Upgrade known platform share links before any network access.
            if ("http".equalsIgnoreCase(uri.getScheme())) uri = URI.create("https:" + uri.toString().substring(5));
            return new MediaInput(uri, MediaPlatform.identify(uri));
        } catch (IllegalArgumentException invalid) { throw new ApiException(ApiError.INVALID_ARGUMENT); }
    }
}
