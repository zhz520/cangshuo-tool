package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

/** Platform field mapping adapted from parse-video-py (MIT); see docs/SHORT_VIDEO_PARSE.md and NOTICES. */
@Component
public class MediaParser {
    public record Candidate(String kind, String label, URI uri, int width, int height, long bitrate, String source) {}
    public record Parsed(MediaPlatform platform, URI sourceUrl, String title, String description,
                         String author, long durationSeconds, List<Candidate> assets) {}
    private final MediaHttp http;
    private final MediaJson json;
    public MediaParser(MediaHttp http, MediaJson json) { this.http = http; this.json = json; }

    public Parsed parse(MediaInput input) {
        long deadline = MediaHttp.deadline(30);
        try {
            return switch (input.platform()) {
                case DOUYIN -> douyin(input, deadline);
                case KUAISHOU -> kuaishou(input, deadline);
                case XIAOHONGSHU -> xiaohongshu(input, deadline);
                case BILIBILI -> bilibili(input, deadline);
                case WEIBO -> weibo(input, deadline);
                case XIGUA -> xigua(input, deadline);
                case PIPIXIA -> pipixia(input, deadline);
                case TIKTOK -> tiktok(input, deadline);
            };
        } catch (ApiException error) { throw error; }
        catch (Exception invalid) { throw new ApiException(ApiError.MEDIA_UNAVAILABLE); }
    }
    private MediaHttp.Document page(MediaInput input, URI uri, boolean desktop, long deadline) {
        return http.document(input.platform(), uri, null, desktop, deadline);
    }
    private JsonNode get(MediaInput input, String url, long deadline) { return json.json(page(input, URI.create(url), true, deadline).text()); }
    private Parsed douyin(MediaInput input, long deadline) {
        String id = numericId(input.uri());
        MediaHttp.Document initial = null;
        if (id.isEmpty()) { initial = page(input, input.uri(), false, deadline); id = numericId(initial.uri()); }
        if (id.isEmpty()) throw unavailable();
        JsonNode data = null;
        for (String extra : List.of("", "&request_source=200")) {
            try {
                JsonNode api = get(input, "https://www.iesdouyin.com/web/api/v2/aweme/slidesinfo/?aweme_ids=%5B" + id + "%5D" + extra, deadline);
                if (api.path("aweme_details").isArray() && !api.path("aweme_details").isEmpty()) { data = api.path("aweme_details").get(0); break; }
            } catch (ApiException error) { if (error.error() == ApiError.MEDIA_TIMEOUT || error.error() == ApiError.MEDIA_TOO_LARGE) throw error; }
        }
        if (data == null) {
            String html = initial == null ? page(input, URI.create("https://www.iesdouyin.com/share/video/" + id + "/"), false, deadline).text() : initial.text();
            data = routerItem(json.assignment(html, "_ROUTER_DATA"));
        }
        return byteDance(input, data);
    }
    private Parsed byteDance(MediaInput input, JsonNode data) {
        var assets = new ArrayList<Candidate>();
        JsonNode video = data.path("video");
        JsonNode images = data.path("images");
        if (images.isArray() && !images.isEmpty()) {
            checkImages(images.size());
            for (JsonNode image : images) {
                add(assets, input, "IMAGE", "", firstUrl(image.path("url_list")), image.path("width").asInt(0), image.path("height").asInt(0), 0, "PLATFORM_SOURCE");
                String live = firstUrl(image.at("/video/play_addr/url_list"));
                if (!live.isBlank()) add(assets, input, "LIVE", "", noPlayWatermark(live), 0, 0, 0, "PLATFORM_SOURCE");
            }
        } else {
            JsonNode rates = video.path("bit_rate");
            if (rates.size() > 30) throw large();
            var videos = new ArrayList<Candidate>();
            for (JsonNode rate : rates) {
                JsonNode address = rate.path("play_addr");
                String url = firstUrl(address.path("url_list"));
                if (!url.isBlank()) add(videos, input, "VIDEO", "", noPlayWatermark(url), address.path("width").asInt(0), address.path("height").asInt(0), rate.path("bit_rate").asLong(0), "PLATFORM_SOURCE");
            }
            videos.sort(Comparator.comparingLong(Candidate::bitrate).reversed());
            assets.addAll(videos);
            String play = firstUrl(video.at("/play_addr/url_list"));
            if (!play.isBlank()) add(assets, input, "VIDEO", "", noPlayWatermark(play), video.path("width").asInt(0), video.path("height").asInt(0), 0, "PLATFORM_SOURCE");
        }
        String cover = firstUrl(video.at("/origin_cover/url_list"));
        if (cover.isBlank()) cover = firstUrl(video.at("/cover/url_list"));
        addCover(assets, input, cover);
        return result(input, data.path("desc").asText(""), data.path("desc").asText(""),
                data.at("/author/nickname").asText(""), video.path("duration").asLong(0) / 1000, assets);
    }
    static String noPlayWatermark(String raw) {
        // Only the named playback path is switched. Do not strip arbitrary query parameters/signatures.
        return raw.replace("/playwm/", "/play/");
    }
    private Parsed kuaishou(MediaInput input, long deadline) {
        var doc = page(input, input.uri(), false, deadline);
        JsonNode state = json.assignment(doc.text(), "INIT_STATE");
        JsonNode photo = null;
        for (JsonNode row : state) if (row.path("result").asInt(0) == 1 && row.path("photo").isObject()) { photo = row.path("photo"); break; }
        if (photo == null) throw unavailable();
        var assets = new ArrayList<Candidate>();
        JsonNode atlas = photo.at("/ext_params/atlas");
        if (atlas.path("list").isArray() && !atlas.path("list").isEmpty()) {
            checkImages(atlas.path("list").size());
            String cdn = atlas.path("cdn").path(0).asText("");
            for (JsonNode image : atlas.path("list")) add(assets, input, "IMAGE", "", "https://" + cdn + "/" + image.asText(), 0, 0, 0, "PLATFORM_SOURCE");
        } else for (JsonNode mv : photo.path("mainMvUrls")) add(assets, input, "VIDEO", "", mv.path("url").asText(""), 0, 0, 0, "PLATFORM_SOURCE");
        addCover(assets, input, photo.path("coverUrls").path(0).path("url").asText(""));
        return result(input, photo.path("caption").asText(""), "", photo.path("userName").asText(""), photo.path("duration").asLong(0) / 1000, assets);
    }
    private Parsed xiaohongshu(MediaInput input, long deadline) {
        var doc = page(input, input.uri(), true, deadline);
        JsonNode state = json.assignment(doc.text(), "__INITIAL_STATE__").path("note");
        String id = state.path("currentNoteId").asText("");
        if (id.isBlank() || id.equals("undefined")) throw unavailable();
        JsonNode note = state.path("noteDetailMap").path(id).path("note");
        var assets = new ArrayList<Candidate>();
        for (JsonNode stream : note.at("/video/media/stream/h264")) {
            String master = stream.path("masterUrl").asText("");
            if (!master.isBlank()) add(assets, input, "VIDEO", "", master, stream.path("width").asInt(0), stream.path("height").asInt(0), stream.path("avgBitrate").asLong(0), "PLATFORM_SOURCE");
        }
        assets.sort(Comparator.comparingLong(Candidate::bitrate).reversed());
        JsonNode images = note.path("imageList");
        if (assets.isEmpty()) {
            checkImages(images.size());
            for (JsonNode image : images) {
                String url = image.path("urlDefault").asText("");
                String source = "UNKNOWN";
                if (url.contains("/notes_pre_post/")) {
                    URI owned = input.platform().mediaUri(url);
                    String imageId = owned.getPath().substring(owned.getPath().lastIndexOf('/') + 1).split("!", 2)[0];
                    if (!imageId.matches("[A-Za-z0-9_-]{8,200}")) throw unavailable();
                    url = "https://ci.xiaohongshu.com/notes_pre_post/" + (url.contains("/spectrum/") ? "spectrum/" : "") + imageId + "?imageView2/format/jpg";
                    source = "PLATFORM_SOURCE";
                }
                add(assets, input, "IMAGE", "", url, image.path("width").asInt(0), image.path("height").asInt(0), 0, source);
                if (image.path("livePhoto").asBoolean(false)) {
                    String live = image.at("/stream/h264/0/masterUrl").asText("");
                    if (!live.isBlank()) add(assets, input, "LIVE", "", live, 0, 0, 0, "PLATFORM_SOURCE");
                }
            }
        }
        addCover(assets, input, images.path(0).path("urlDefault").asText(""));
        return result(input, note.path("title").asText(""), note.path("desc").asText(""), note.at("/user/nickname").asText(""), note.at("/video/capa/duration").asLong(0), assets);
    }
    private Parsed bilibili(MediaInput input, long deadline) {
        URI uri = input.uri();
        if (uri.getHost().equals("b23.tv")) uri = page(input, uri, true, deadline).uri();
        Matcher matcher = Pattern.compile("/video/(BV[A-Za-z0-9]{10}|av[0-9]{1,20})(?:/|$)").matcher(uri.getPath());
        if (!matcher.find()) throw unavailable();
        String id = matcher.group(1), key = id.startsWith("BV") ? "bvid=" + id : "aid=" + id.substring(2);
        int pageNumber = 1;
        String pageQuery = query(uri, "p");
        if (!pageQuery.isEmpty()) { pageNumber = Integer.parseInt(pageQuery); if (pageNumber < 1 || pageNumber > 100) throw unavailable(); }
        JsonNode view = get(input, "https://api.bilibili.com/x/web-interface/view?" + key, deadline);
        if (view.path("code").asInt(-1) != 0) throw unavailable();
        JsonNode data = view.path("data"), part = data.path("pages").path(pageNumber - 1);
        long cid = part.path("cid").asLong(0);
        if (cid <= 0) throw unavailable();
        JsonNode play = get(input, "https://api.bilibili.com/x/player/playurl?otype=json&fnver=0&fnval=0&qn=80&platform=html5&" + key + "&cid=" + cid, deadline);
        if (play.path("code").asInt(-1) != 0) throw unavailable();
        JsonNode durl = play.at("/data/durl");
        if (durl.size() != 1) throw new ApiException(ApiError.MEDIA_FORMAT_UNSUPPORTED);
        var assets = new ArrayList<Candidate>();
        String label = "";
        JsonNode qualities = play.at("/data/accept_quality");
        for (int i = 0; i < qualities.size(); i++) if (qualities.get(i).asInt() == play.at("/data/quality").asInt())
            label = play.at("/data/accept_description").path(i).asText("");
        add(assets, input, "VIDEO", label, durl.path(0).path("url").asText(""), 0, 0, 0, "PLATFORM_SOURCE");
        addCover(assets, input, data.path("pic").asText(""));
        return result(input, data.path("title").asText("") + (pageNumber > 1 ? " · P" + pageNumber : ""), data.path("desc").asText(""), data.at("/owner/name").asText(""), part.path("duration").asLong(0), assets);
    }
    private Parsed weibo(MediaInput input, long deadline) {
        URI uri = input.uri();
        if (uri.getHost().equals("t.cn")) uri = page(input, uri, true, deadline).uri();
        String fid = query(uri, "fid");
        if (fid.isEmpty()) {
            var match = Pattern.compile("/(?:tv/)?show/([A-Za-z0-9:_-]+)").matcher(uri.getPath());
            if (match.find()) fid = match.group(1);
        }
        var assets = new ArrayList<Candidate>();
        if (!fid.isEmpty()) {
            if (!fid.matches("[A-Za-z0-9:_-]{1,80}")) throw unavailable();
            String encoded = URLEncoder.encode(fid, StandardCharsets.UTF_8);
            String body = "data=" + URLEncoder.encode("{\"Component_Play_Playinfo\":{\"oid\":\"" + fid + "\"}}", StandardCharsets.UTF_8);
            JsonNode data = json.json(http.document(input.platform(), URI.create("https://h5.video.weibo.com/api/component?page=/show/" + encoded), body, false, deadline).text()).at("/data/Component_Play_Playinfo");
            Iterator<Map.Entry<String, JsonNode>> entries = data.path("urls").fields();
            while (entries.hasNext()) { var entry = entries.next(); add(assets, input, "VIDEO", entry.getKey(), entry.getValue().asText(""), 0, 0, 0, "PLATFORM_SOURCE"); }
            if (assets.isEmpty()) add(assets, input, "VIDEO", "", data.path("stream_url").asText(""), 0, 0, 0, "PLATFORM_SOURCE");
            addCover(assets, input, data.path("cover_image").asText(""));
            return result(input, data.path("title").asText(""), "", data.path("author").asText(""), data.path("duration").asLong(0), assets);
        }
        String post = uri.getPath().replaceAll("/+$", "");
        post = post.substring(post.lastIndexOf('/') + 1);
        if (!post.matches("[A-Za-z0-9]{5,40}")) throw unavailable();
        JsonNode response = get(input, "https://m.weibo.cn/statuses/show?id=" + post, deadline);
        JsonNode data = response.path("data");
        if (!data.isObject()) throw unavailable();
        JsonNode media = data.at("/page_info/media_info");
        for (String key : List.of("stream_url_hd", "mp4_720p_mp4", "stream_url", "mp4_hd_url", "mp4_sd_url")) {
            String url = media.path(key).asText("");
            if (!url.isBlank()) add(assets, input, "VIDEO", key, url, 0, 0, 0, "PLATFORM_SOURCE");
        }
        JsonNode pics = data.path("pics");
        checkImages(pics.size());
        for (JsonNode pic : pics) {
            String url = pic.at("/large/url").asText("");
            if (url.isBlank()) url = pic.path("url").asText("");
            add(assets, input, "IMAGE", "", url, 0, 0, 0, "UNKNOWN");
        }
        addCover(assets, input, data.at("/page_info/page_pic").asText(""));
        return result(input, data.path("text").asText("").replaceAll("<[^>]*>", ""), "", data.at("/user/screen_name").asText(""), media.path("duration").asLong(0), assets);
    }
    private Parsed xigua(MediaInput input, long deadline) {
        String id = numericId(input.uri());
        if (id.isEmpty()) id = numericId(page(input, input.uri(), false, deadline).uri());
        if (id.isEmpty()) throw unavailable();
        String url = "https://m.ixigua.com/douyin/share/video/" + id + "?aweme_type=107&schema_type=1&utm_source=copy&utm_campaign=client_share&utm_medium=android&app=aweme";
        return byteDance(input, routerItem(json.assignment(page(input, URI.create(url), false, deadline).text(), "_ROUTER_DATA")));
    }
    private Parsed pipixia(MediaInput input, long deadline) {
        String id = numericId(input.uri());
        if (id.isEmpty()) id = numericId(page(input, input.uri(), false, deadline).uri());
        if (id.isEmpty()) throw unavailable();
        JsonNode root = get(input, "https://api.pipix.com/bds/cell/cell_comment/?offset=0&cell_type=1&api_version=1&cell_id=" + id + "&ac=wifi&channel=huawei_1319_64&aid=1319&app_name=super", deadline);
        if (root.path("status_code").asInt(-1) != 0) throw unavailable();
        JsonNode data = root.at("/data/cell_comments/0/comment_info/item");
        var assets = new ArrayList<Candidate>();
        JsonNode images = data.at("/note/multi_image");
        checkImages(images.size());
        for (JsonNode image : images) add(assets, input, "IMAGE", "", firstUrl(image.path("url_list")), 0, 0, 0, "PLATFORM_SOURCE");
        String video = firstUrl(data.at("/video/video_high/url_list"));
        if (!video.isBlank()) add(assets, input, "VIDEO", "", video, 0, 0, 0, "UNKNOWN");
        addCover(assets, input, firstUrl(data.at("/cover/url_list")));
        return result(input, data.path("content").asText(""), "", data.at("/author/name").asText(""), 0, assets);
    }
    private Parsed tiktok(MediaInput input, long deadline) {
        var doc = page(input, input.uri(), true, deadline);
        JsonNode data;
        try { data = json.script(doc.text(), "__UNIVERSAL_DATA_FOR_REHYDRATION__").at("/__DEFAULT_SCOPE__/webapp.video-detail/itemInfo/itemStruct"); }
        catch (ApiException absent) {
            String id = numericId(doc.uri());
            data = json.script(doc.text(), "SIGI_STATE").path("ItemModule").path(id);
        }
        if (!data.isObject()) throw unavailable();
        var assets = new ArrayList<Candidate>();
        JsonNode images = data.at("/imagePost/images");
        if (images.isArray() && !images.isEmpty()) {
            checkImages(images.size());
            for (JsonNode image : images) add(assets, input, "IMAGE", "", firstUrl(image.at("/imageURL/urlList")), 0, 0, 0, "PLATFORM_SOURCE");
        } else {
            JsonNode video = data.path("video");
            for (JsonNode rate : video.path("bitrateInfo")) {
                String url = firstUrl(rate.at("/PlayAddr/UrlList"));
                if (!url.isBlank()) add(assets, input, "VIDEO", "", url, video.path("width").asInt(0), video.path("height").asInt(0), rate.path("Bitrate").asLong(0), "PLATFORM_SOURCE");
            }
            assets.sort(Comparator.comparingLong(Candidate::bitrate).reversed());
            JsonNode play = video.path("playAddr");
            String url = play.isTextual() ? play.asText() : firstUrl(play);
            if (!url.isBlank()) add(assets, input, "VIDEO", "", url, video.path("width").asInt(0), video.path("height").asInt(0), 0, "PLATFORM_SOURCE");
        }
        String cover = data.at("/video/originCover").asText("");
        if (cover.isBlank()) cover = data.at("/video/cover").asText("");
        addCover(assets, input, cover);
        return result(input, data.path("desc").asText(""), "", data.at("/author/nickname").asText(""), data.at("/video/duration").asLong(0), assets);
    }
    private static JsonNode routerItem(JsonNode root) {
        for (JsonNode loader : root.path("loaderData")) {
            JsonNode items = loader.at("/videoInfoRes/item_list");
            if (items.isArray() && !items.isEmpty()) return items.get(0);
        }
        throw unavailable();
    }
    static String firstUrl(JsonNode list) {
        if (list.isTextual()) return list.asText();
        for (JsonNode row : list) {
            String value = row.isTextual() ? row.asText() : row.path("url").asText(row.path("src").asText(""));
            if (!value.isBlank()) return value;
        }
        return "";
    }
    static String numericId(URI uri) {
        String modal = query(uri, "modal_id");
        if (modal.matches("[0-9]{5,25}")) return modal;
        Matcher matcher = Pattern.compile("(?:^|/)([0-9]{5,25})(?:/|$)").matcher(uri.getPath());
        String id = ""; while (matcher.find()) id = matcher.group(1); return id;
    }
    static String query(URI uri, String key) {
        if (uri.getRawQuery() == null) return "";
        for (String part : uri.getRawQuery().split("&")) {
            String[] value = part.split("=", 2);
            if (value.length == 2 && value[0].equals(key)) return URLDecoder.decode(value[1], StandardCharsets.UTF_8);
        }
        return "";
    }
    private static void add(List<Candidate> list, MediaInput input, String kind, String label, String raw, int width, int height, long bitrate, String source) {
        if (raw.isBlank()) throw unavailable();
        URI uri = input.platform().mediaUri(raw);
        if ((kind.equals("VIDEO") || kind.equals("COVER")) && list.stream().anyMatch(a -> a.kind().equals(kind) && a.uri().equals(uri))) return;
        if (list.size() >= 120) throw large();
        list.add(new Candidate(kind, clean(label, 100), uri, Math.max(0, width), Math.max(0, height), Math.max(0, bitrate), source));
    }
    private static void addCover(List<Candidate> list, MediaInput input, String url) {
        if (url.isBlank()) url = list.stream().filter(a -> a.kind().equals("IMAGE")).findFirst().map(a -> a.uri().toString()).orElse("");
        if (!url.isBlank()) add(list, input, "COVER", "", url, 0, 0, 0, "UNKNOWN");
    }
    private static Parsed result(MediaInput input, String title, String description, String author, long duration, List<Candidate> assets) {
        if (assets.stream().noneMatch(a -> a.kind().equals("VIDEO") || a.kind().equals("IMAGE"))) throw unavailable();
        checkImages((int) assets.stream().filter(a -> a.kind().equals("IMAGE")).count());
        return new Parsed(input.platform(), input.uri(), clean(title, 4000), clean(description, 20000), clean(author, 200), Math.max(0, Math.min(86400, duration)), List.copyOf(assets));
    }
    private static String clean(String value, int max) {
        return value.codePoints().filter(c -> !Character.isISOControl(c) || c == 10)
                .filter(c -> c != 0x202e && c != 0x202d && c != 0x2066 && c != 0x2067 && c != 0x2068 && c != 0x2069)
                .limit(max).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString().strip();
    }
    private static void checkImages(int count) { if (count > 50) throw large(); }
    private static ApiException unavailable() { return new ApiException(ApiError.MEDIA_UNAVAILABLE); }
    private static ApiException large() { return new ApiException(ApiError.MEDIA_TOO_LARGE); }
}
