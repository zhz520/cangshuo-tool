package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** H5 atlas and Apollo references; independent mapping based on the sources recorded in SHORT_VIDEO_PARSE.md. */
final class KuaishouMetadata {
    record Payload(JsonNode photo, JsonNode atlas, String author, JsonNode client) {}
    record Video(List<String> urls, int width, int height, long bitrate) {}

    static String photoId(URI uri) {
        var match = Pattern.compile("/(?:fw/(?:photo|long-video)|short-video)/([A-Za-z0-9]{5,40})(?:/|$)").matcher(uri.getPath());
        if (match.find()) return match.group(1);
        String query = MediaParser.query(uri, "photoId");
        return query.matches("[A-Za-z0-9]{5,40}") ? query : "";
    }
    static Payload extract(JsonNode state, String id, MediaJson json) {
        JsonNode client = state.path("defaultClient");
        JsonNode photo = client.path("VisionVideoDetailPhoto:" + id);
        String author = "";
        if (!photo.isObject()) {
            JsonNode payload = state.path("photo").isObject() ? state : null;
            if (payload == null) for (JsonNode row : state) {
                if (row.path("result").asInt(0) == 1 && row.path("photo").isObject()) { payload = row; break; }
            }
            if (payload == null) throw unavailable();
            photo = payload.path("photo");
            return new Payload(photo, payload.path("atlas"), photo.path("userName").asText(""), client);
        }
        // Only the requested Apollo object is read, never a recommended photo or an arbitrary author's name.
        JsonNode detail = client.path("$ROOT_QUERY.visionVideoDetail({\"photoId\":\"" + id + "\",\"page\":\"detail\"})");
        JsonNode user = resolve(detail.path("author"), client);
        author = user.path("name").asText(photo.path("userName").asText(""));
        return new Payload(photo, photo.path("atlas"), author, client);
    }
    private static JsonNode decoded(JsonNode node, MediaJson json) {
        return node.isTextual() && !node.asText().isBlank() ? json.json(node.asText()) : node;
    }
    static List<List<String>> images(Payload payload, MediaJson json) {
        JsonNode photo = payload.photo(), ext = decoded(photo.path("ext_params"), json);
        var variants = List.of(decoded(ext.path("atlas"), json), decoded(payload.atlas(), json), decoded(photo.path("atlas"), json));
        for (JsonNode raw : variants) {
            JsonNode atlas = resolve(raw, payload.client());
            JsonNode images = atlas.isArray() ? atlas : atlas.path("list");
            if (!images.isArray() || images.isEmpty()) continue;
            if (images.size() > 50) throw new ApiException(ApiError.MEDIA_TOO_LARGE);
            var cdns = MediaParser.urls(atlas.path("cdn"));
            if (cdns.isEmpty()) for (JsonNode cdn : atlas.path("cdnList")) {
                String host = cdn.path("cdn").asText("");
                if (!host.isBlank() && cdns.size() < 4) cdns.add(host);
            }
            var result = new ArrayList<List<String>>();
            for (JsonNode rawImage : images) {
                JsonNode image = resolve(rawImage, payload.client());
                var urls = image.isTextual() ? MediaParser.urls(image) : MediaParser.urls(image.path("cdnUrls"));
                if (urls.isEmpty()) urls = MediaParser.urls(image.path("url"));
                if (urls.isEmpty()) throw unavailable();
                var addresses = new ArrayList<String>();
                for (String path : urls) {
                    if (path.startsWith("https://") || path.startsWith("http://") || path.startsWith("//")) addresses.add(path);
                    else for (String cdn : cdns) {
                        String prefix = cdn.startsWith("http") ? cdn : cdn.startsWith("//") ? "https:" + cdn : "https://" + cdn;
                        addresses.add(prefix.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", ""));
                    }
                }
                if (addresses.isEmpty()) throw unavailable();
                result.add(List.copyOf(addresses));
            }
            return List.copyOf(result);
        }
        return List.of();
    }
    static List<Video> videos(Payload payload, MediaJson json) {
        JsonNode photo = payload.photo(), client = payload.client();
        var result = new ArrayList<Video>();
        JsonNode manifest = resolve(decoded(photo.path("manifest"), json), client);
        if (manifest.has("json")) manifest = decoded(manifest.path("json"), json);
        for (JsonNode rawSet : manifest.path("adaptationSet")) {
            JsonNode set = resolve(rawSet, client);
            for (JsonNode rawRepresentation : set.path("representation")) {
                if (result.size() >= 30) throw new ApiException(ApiError.MEDIA_TOO_LARGE);
                JsonNode representation = resolve(rawRepresentation, client);
                var urls = addressUrls(representation.path("url"), client);
                urls.addAll(addressUrls(representation.path("backupUrl"), client));
                if (!urls.isEmpty()) result.add(new Video(List.copyOf(urls), representation.path("width").asInt(0),
                        representation.path("height").asInt(0), representation.path("avgBitrate").asLong(representation.path("bitrate").asLong(0))));
            }
        }
        result.sort(Comparator.comparingLong(Video::bitrate).reversed());
        var basic = MediaParser.urls(photo.path("mainMvUrls"));
        if (basic.isEmpty()) basic = MediaParser.urls(photo.path("photoUrl"));
        if (!basic.isEmpty()) result.add(new Video(List.copyOf(basic), photo.path("width").asInt(0), photo.path("height").asInt(0), 0));
        return List.copyOf(result);
    }
    private static ArrayList<String> addressUrls(JsonNode raw, JsonNode client) {
        JsonNode node = resolve(raw, client);
        return MediaParser.urls(node.has("json") ? node.path("json") : node);
    }
    static JsonNode resolve(JsonNode node, JsonNode client) {
        var visited = new HashSet<String>();
        for (int hop = 0; hop < 10; hop++) {
            String id = node.path("__ref").asText(node.path("type").asText("").equals("id") ? node.path("id").asText("") : "");
            if (id.isEmpty()) return node;
            if (!visited.add(id)) throw unavailable();
            node = client.path(id);
            if (node.isMissingNode()) throw unavailable();
        }
        throw unavailable();
    }
    private static ApiException unavailable() { return new ApiException(ApiError.MEDIA_UNAVAILABLE); }
}
