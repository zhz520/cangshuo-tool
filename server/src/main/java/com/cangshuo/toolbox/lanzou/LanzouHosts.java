package com.cangshuo.toolbox.lanzou;

import com.cangshuo.toolbox.common.exception.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Only official Lanzou host families may be fetched or followed. */
final class LanzouHosts {
    private static final Pattern OWNED = Pattern.compile(
            "(?i)^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)?lanzou[a-z]?\\.com$");
    private static final Pattern IP_LITERAL = Pattern.compile("(?i)^(?:[0-9.]+|\\[[0-9a-f:]+]|[0-9a-f:]+)$");
    private static final List<Pattern> DOWNLOAD_ROOTS = List.of(
            Pattern.compile("(?i)^(?:[a-z0-9-]+\\.)+dmpdmp\\.com$"),
            Pattern.compile("(?i)^(?:[a-z0-9-]+\\.)+lanrar\\.com$"),
            Pattern.compile("(?i)^(?:[a-z0-9-]+\\.)+woozooo\\.com$"),
            Pattern.compile("(?i)^(?:[a-z0-9-]+\\.)+baidupan\\.com$"));

    private LanzouHosts() {}

    static URI page(URI uri) {
        if (!isOwned(uri)) throw new ApiException(ApiError.LANZOU_LINK_INVALID);
        pageShape(uri, ApiError.LANZOU_LINK_INVALID);
        return uri;
    }

    private static void pageShape(URI uri, ApiError error) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getRawFragment() != null
                || uri.getPort() != -1 && uri.getPort() != 443
                || uri.getPath() == null || uri.getPath().isBlank() || uri.getPath().length() > 2048
                || uri.toASCIIString().length() > 4096 || hasControl(uri.toString()))
            throw new ApiException(error);
    }

    static URI ownedPage(URI uri) {
        if (!isOwned(uri)) throw new ApiException(ApiError.LANZOU_PROVIDER_ERROR);
        page(uri);
        return uri;
    }

    static boolean isOwned(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getHost().isBlank()) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return OWNED.matcher(host).matches() && !host.endsWith(".") && !host.contains("..");
    }

    static URI downloadPage(String base, String token) {
        if (base == null || token == null || token.isBlank() || token.length() > 2048 || hasControl(token))
            throw new ApiException(ApiError.LANZOU_PROVIDER_ERROR);
        try {
            URI root = URI.create(base);
            validateDownloadBase(root);
            if (!"https".equalsIgnoreCase(root.getScheme()) || root.getUserInfo() != null
                    || root.getRawFragment() != null || root.getPort() != -1 && root.getPort() != 443)
                throw new ApiException(ApiError.LANZOU_PROVIDER_ERROR);
            String tail = token.startsWith("/") ? token : "/" + token;
            URI result = root.resolve(tail.startsWith("/file/") ? tail : "/file/" + tail);
            return downloadPage(result);
        } catch (ApiException error) { throw error; }
        catch (IllegalArgumentException error) {
            throw new ApiException(ApiError.LANZOU_PROVIDER_ERROR);
        }
    }

    static URI downloadPage(URI uri) {
        validateDownloadBase(uri);
        pageShape(uri, ApiError.LANZOU_PROVIDER_ERROR);
        return uri;
    }

    private static void validateDownloadBase(URI uri) {
        if (uri == null || uri.getHost() == null) throw new ApiException(ApiError.LANZOU_PROVIDER_ERROR);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!isOwned(uri) && DOWNLOAD_ROOTS.stream().noneMatch(root -> root.matcher(host).matches()))
            throw new ApiException(ApiError.LANZOU_PROVIDER_ERROR);
    }

    static URI direct(String value) {
        try {
            String clean = value == null ? "" : value.strip();
            if (clean.startsWith("//")) clean = "https:" + clean;
            URI uri = URI.create(clean);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || uri.getHost() == null || uri.getHost().isBlank()
                    || !uri.getHost().contains(".") || Set.of("localhost", "localhost.localdomain")
                    .contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || IP_LITERAL.matcher(uri.getHost()).matches() || uri.getUserInfo() != null
                    || uri.getRawFragment() != null || uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443
                    || uri.toASCIIString().length() > 8192 || hasControl(clean))
                throw new ApiException(ApiError.LANZOU_PROVIDER_ERROR);
            return stripPid(uri);
        } catch (ApiException error) {
            throw error;
        } catch (IllegalArgumentException error) {
            throw new ApiException(ApiError.LANZOU_PROVIDER_ERROR);
        }
    }

    static URI stripPid(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) return uri;
        List<String> kept = new ArrayList<>();
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String rawName = equals < 0 ? pair : pair.substring(0, equals);
            String name;
            try { name = URLDecoder.decode(rawName, StandardCharsets.UTF_8); }
            catch (IllegalArgumentException invalid) { name = rawName; }
            if (!"pid".equalsIgnoreCase(name) && !pair.isEmpty()) kept.add(pair);
        }
        try {
            String queryValue = kept.isEmpty() ? "" : "?" + String.join("&", kept);
            return URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath() + queryValue);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ApiError.LANZOU_PROVIDER_ERROR);
        }
    }

    static boolean hasControl(String value) {
        return value != null && value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
    }
}
