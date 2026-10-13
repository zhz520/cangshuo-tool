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
    public record Candidate(String kind, String label, URI uri, int width, int height, long bitrate, String source,
                            List<URI> fallbacks) {
        public Candidate(String kind, String label, URI uri, int width, int height, long bitrate, String source) {
            this(kind, label, uri, width, height, bitrate, source, List.of());
        }
        public Candidate { fallbacks = List.copyOf(fallbacks); }
    }
    public record Parsed(MediaPlatform platform, URI sourceUrl, String title, String description,
                         String author, long durationSeconds, List<Candidate> assets) {}
    private final MediaHttp http;
    private final MediaJson json;
    private final MediaBrowser browser;
    @org.springframework.beans.factory.annotation.Autowired
    public MediaParser(MediaHttp http, MediaJson json, MediaBrowser browser) {
        this.http = http; this.json = json; this.browser = browser;
    }
    MediaParser(MediaHttp http, MediaJson json) { this(http, json, new MediaBrowser("", json)); }

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
    /** Mobile app channel: that node rejects browser user agents and needs no signature. */
    private JsonNode getApp(MediaInput input, String url, long deadline) {
        return json.json(http.document(input.platform(), URI.create(url), null, MediaHttp.APP_UA, deadline).text());
    }
    private Parsed douyin(MediaInput input, long deadline) {
        String id = numericId(input.uri());
        MediaHttp.Document initial = null;
        if (id.isEmpty()) { initial = page(input, input.uri(), false, deadline); id = numericId(initial.uri()); }
        if (id.isEmpty()) throw unavailable();
        boolean note = isNote(initial == null ? input.uri() : initial.uri());
        JsonNode data = null;
        ApiException failure = null;
        // Reuse the document already fetched to resolve the short link, before making more upstream requests.
        if (initial != null) {
            try { data = matchingItem(json.assignment(initial.text(), "_ROUTER_DATA"), id); }
            catch (ApiException error) { failure = error; }
        }
        if (data != null) return byteDance(input, data);
        // Both publicly documented hosts expose this API, but can return different edge responses.
        for (String host : List.of("www.douyin.com", "www.iesdouyin.com")) {
            for (String extra : note ? List.of("&request_source=200", "") : List.of("", "&request_source=200")) {
                try {
                    JsonNode api = json.json(page(input, URI.create("https://" + host
                            + "/web/api/v2/aweme/slidesinfo/?aweme_ids=%5B" + id + "%5D" + extra), false, deadline).text());
                    data = matchingItem(api, id);
                    if (data != null) break;
                    // Anonymous image posts are filtered with reason 8. This is not evidence of deletion.
                    if (api.path("filter_list").isArray() && !api.path("filter_list").isEmpty()) {
                        failure = new ApiException(ApiError.MEDIA_PLATFORM_RESTRICTED);
                        if (note) break;
                    }
                } catch (ApiException error) { fatalFallback(error, deadline); failure = preferFailure(failure, error); }
            }
            if (data != null) break;
        }
        if (data == null && !note) data = mobileFeed(input, id, deadline);
        if (data == null) {
            for (String kind : note ? List.of("note", "video") : List.of("video", "note")) {
                try {
                    URI uri = URI.create("https://www.iesdouyin.com/share/" + kind + "/" + id + "/");
                    // A failed first route must not abort the alternative. Do not refetch identical documents.
                    if (initial != null && initial.uri().equals(uri)) continue;
                    data = matchingItem(json.assignment(page(input, uri, false, deadline).text(), "_ROUTER_DATA"), id);
                    if (data != null) break;
                } catch (ApiException error) { fatalFallback(error, deadline); failure = preferFailure(failure, error); }
            }
        }
        if (data == null && browser.enabled()) data = browser.douyin(id, deadline);
        if (data == null) throw failure == null ? unavailable() : failure;
        return byteDance(input, data);
    }
    private static ApiException preferFailure(ApiException previous, ApiException current) {
        return previous != null && previous.error() == ApiError.MEDIA_PLATFORM_RESTRICTED ? previous : current;
    }
    private JsonNode mobileFeed(MediaInput input, String id, long deadline) {
        for (String host : List.of("https://api5-normal-c-hl.amemv.com", "https://aweme.snssdk.com")) {
            try {
                JsonNode response = getApp(input, host + "/aweme/v1/feed/?aweme_id=" + id + "&aid=1128", deadline);
                for (JsonNode item : response.path("aweme_list"))
                    if (id.equals(item.path("aweme_id").asText("")) && hasByteDanceMedia(item)) return item;
            } catch (ApiException error) { fatalFallback(error, deadline); }
        }
        return null;
    }
    static boolean isNote(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        return path.contains("/note/") || path.contains("/slides/") || path.contains("/share/note/");
    }
    private Parsed byteDance(MediaInput input, JsonNode data) {
        var assets = new ArrayList<Candidate>();
        JsonNode video = data.path("video");
        JsonNode images = byteDanceImages(data);
        if (images.isArray() && !images.isEmpty()) {
            checkImages(images.size());
            for (JsonNode image : images) {
                var urls = urls(image.path("url_list"));
                if (urls.isEmpty()) urls = urls(image.path("urlList"));
                // url_list is the clean display source; download_url_list contains official watermarks.
                Collections.reverse(urls);
                addUrls(assets, input, "IMAGE", "", urls, image.path("width").asInt(0), image.path("height").asInt(0), 0, "PLATFORM_SOURCE");
                var lives = urls(image.at("/video/play_addr/url_list"));
                if (lives.isEmpty()) lives = urls(image.at("/video/playAddr"));
                if (!lives.isEmpty()) addUrls(assets, input, "LIVE", "", lives.stream().map(MediaParser::noPlayWatermark).toList(), 0, 0, 0, "PLATFORM_SOURCE");
            }
        } else {
            JsonNode rates = video.path("bit_rate");
            if (rates.size() > 30) throw large();
            var videos = new ArrayList<Candidate>();
            for (JsonNode rate : rates) {
                JsonNode address = rate.path("play_addr");
                var urls = urls(address.path("url_list"));
                if (!urls.isEmpty()) addUrls(videos, input, "VIDEO", "", urls.stream().map(MediaParser::noPlayWatermark).toList(), address.path("width").asInt(0), address.path("height").asInt(0), rate.path("bit_rate").asLong(0), "PLATFORM_SOURCE");
            }
            videos.sort(Comparator.comparingLong(Candidate::bitrate).reversed());
            assets.addAll(videos);
            String play = firstUrl(video.at("/play_addr/url_list"));
            if (!play.isBlank()) add(assets, input, "VIDEO", "", noPlayWatermark(play), video.path("width").asInt(0), video.path("height").asInt(0), 0, "PLATFORM_SOURCE");
        }
        var cover = urls(video.at("/origin_cover/url_list"));
        if (cover.isEmpty()) cover = urls(video.at("/cover/url_list"));
        addCover(assets, input, cover);
        return result(input, data.path("desc").asText(""), data.path("desc").asText(""),
                data.at("/author/nickname").asText(""), video.path("duration").asLong(0) / 1000, assets);
    }
    static String noPlayWatermark(String raw) {
        // Only the named playback path is switched. Do not strip arbitrary query parameters/signatures.
        return raw.replace("/playwm/", "/play/");
    }
    private Parsed kuaishou(MediaInput input, long deadline) {
        MediaHttp.Document doc = page(input, input.uri(), false, deadline);
        String id = KuaishouMetadata.photoId(doc.uri());
        ApiException failure;
        try { return kuaishouDocument(input, doc, id); }
        catch (ApiException error) { fatalFallback(error, deadline); failure = error; }
        if (!id.isEmpty()) {
            for (String url : List.of("https://v.m.chenzhongtech.com/fw/photo/" + id,
                    "https://www.kuaishou.com/short-video/" + id)) {
                try {
                    URI uri = URI.create(url);
                    if (uri.equals(doc.uri())) continue;
                    return kuaishouDocument(input, page(input, uri, url.contains("short-video"), deadline), id);
                } catch (ApiException error) { fatalFallback(error, deadline); failure = error; }
            }
        }
        throw failure;
    }
    private Parsed kuaishouDocument(MediaInput input, MediaHttp.Document doc, String id) {
        JsonNode state;
        try { state = json.assignment(doc.text(), "INIT_STATE"); }
        catch (ApiException absent) { state = json.assignment(doc.text(), "__APOLLO_STATE__"); }
        var payload = KuaishouMetadata.extract(state, id, json);
        JsonNode photo = payload.photo();
        var assets = new ArrayList<Candidate>();
        var images = KuaishouMetadata.images(payload, json);
        if (!images.isEmpty()) {
            checkImages(images.size());
            for (var image : images) addUrls(assets, input, "IMAGE", "", image, 0, 0, 0, "PLATFORM_SOURCE");
        } else {
            for (var video : KuaishouMetadata.videos(payload, json))
                addUrls(assets, input, "VIDEO", "", video.urls(), video.width(), video.height(), video.bitrate(), "PLATFORM_SOURCE");
        }
        addCover(assets, input, urls(photo.path("coverUrls")));
        return result(input, photo.path("caption").asText(""), "", payload.author(), photo.path("duration").asLong(0) / 1000, assets);
    }
    private Parsed xiaohongshu(MediaInput input, long deadline) {
        // Desktop long links carry the richest state; app share short links render the mobile H5 structure instead.
        String displayId = noteId(input.uri());
        JsonNode note = null;
        URI finalUri = input.uri();
        ApiException failure = null;
        // A stale xsec_token is itself answered with /404, so retry the same path without the share signature.
        var candidates = new ArrayList<URI>();
        candidates.add(input.uri());
        URI unsigned = withoutToken(input.uri());
        if (unsigned != null) candidates.add(unsigned);
        for (URI candidate : candidates) {
            for (boolean desktop : List.of(true, false)) {
                try {
                    var doc = page(input, candidate, desktop, deadline);
                    finalUri = doc.uri();
                    note = noteState(json.assignment(doc.text(), "__INITIAL_STATE__"), displayId);
                    if (note != null) break;
                } catch (ApiException error) {
                    if (error.error() == ApiError.MEDIA_TIMEOUT || error.error() == ApiError.MEDIA_TOO_LARGE) throw error;
                    failure = error;
                }
                // /404 is the platform's answer to rate limiting as well; other user agents will not change it.
                if (note == null && finalUri.toString().contains("/404")) break;
            }
            if (note != null) break;
        }
        if (note == null) throw expiredOrUnavailable(finalUri, failure);
        var assets = new ArrayList<Candidate>();
        var streams = new ArrayList<Candidate>();
        for (JsonNode stream : note.at("/video/media/stream/h264")) {
            String master = stream.path("masterUrl").asText("");
            if (!master.isBlank()) add(streams, input, "VIDEO", "", master, stream.path("width").asInt(0), stream.path("height").asInt(0), stream.path("avgBitrate").asLong(0), "PLATFORM_SOURCE");
        }
        streams.sort(Comparator.comparingLong(Candidate::bitrate).reversed());
        // The author's original upload key is the watermark-free source, so it leads the quality list.
        String origin = note.at("/video/consumer/originVideoKey").asText(note.path("video").path("originVideoKey").asText(""));
        if (!origin.isBlank()) {
            String url = origin.startsWith("http") ? origin : "https://sns-video-bd.xhscdn.com/" + origin.replaceAll("^/+", "");
            try { add(assets, input, "VIDEO", "", url, 0, 0, 0, "PLATFORM_SOURCE"); }
            catch (ApiException error) { if (error.error() == ApiError.MEDIA_TIMEOUT || error.error() == ApiError.MEDIA_TOO_LARGE) throw error; }
        }
        assets.addAll(streams);
        JsonNode images = note.path("imageList");
        if (assets.isEmpty()) {
            checkImages(images.size());
            for (JsonNode image : images) {
                String raw = image.path("urlDefault").asText(image.path("url").asText(""));
                if (raw.isBlank()) {
                    JsonNode info = image.path("infoList");
                    if (info.isArray() && !info.isEmpty()) raw = info.get(info.size() - 1).path("url").asText("");
                }
                String url = xhsImage(raw, image.path("fileId").asText(""));
                if (url.isBlank()) throw unavailable();
                // Only a rewritten address is known to be the platform's clean source; raw ones may carry a watermark.
                boolean clean = url.startsWith("https://sns-img-qc.xhscdn.com/") || url.contains("ci.xiaohongshu.com/notes_pre_post/");
                add(assets, input, "IMAGE", "", url, image.path("width").asInt(0), image.path("height").asInt(0), 0,
                        clean ? "PLATFORM_SOURCE" : "UNKNOWN");
                if (image.path("livePhoto").asBoolean(false)) {
                    String live = image.at("/stream/h264/0/masterUrl").asText("");
                    if (!live.isBlank()) add(assets, input, "LIVE", "", live, 0, 0, 0, "PLATFORM_SOURCE");
                }
            }
        }
        addCover(assets, input, xhsImage(images.path(0).path("urlDefault").asText(images.path(0).path("url").asText("")), images.path(0).path("fileId").asText("")));
        return result(input, note.path("title").asText(""), note.path("desc").asText(""), note.at("/user/nickname").asText(note.at("/user/nickName").asText("")), note.at("/video/capa/duration").asLong(0), assets);
    }
    static String noteId(URI uri) {
        Matcher matcher = Pattern.compile("/(?:explore|discovery/item)/([0-9a-f]{16,32})").matcher(uri.getPath() == null ? "" : uri.getPath());
        return matcher.find() ? matcher.group(1) : "";
    }
    /** Drops the share signature parameters, keeping every other query value intact. */
    static URI withoutToken(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || !query.contains("xsec_token")) return null;
        StringBuilder kept = new StringBuilder();
        for (String part : query.split("&")) {
            String name = part.split("=", 2)[0];
            if (name.startsWith("xsec_")) continue;
            if (kept.length() > 0) kept.append('&');
            kept.append(part);
        }
        try {
            return new URI(uri.getScheme(), uri.getRawAuthority(), uri.getPath(),
                    kept.length() == 0 ? null : kept.toString(), null);
        } catch (URISyntaxException invalid) { return null; }
    }
    /** Desktop state (note.noteDetailMap) and mobile H5 state (noteData.data.noteData) are both in the wild. */
    static JsonNode noteState(JsonNode root, String displayId) {
        JsonNode note = root.path("note");
        for (String key : List.of(displayId, note.path("firstNoteId").asText(""), note.path("currentNoteId").asText(""))) {
            if (key == null || key.isBlank() || key.equals("undefined")) continue;
            JsonNode candidate = note.path("noteDetailMap").path(key).path("note");
            if (candidate.isObject() && candidate.size() > 0) return candidate;
        }
        JsonNode mobile = root.path("noteData").path("data").path("noteData");
        return mobile.isObject() && mobile.size() > 0 ? mobile : null;
    }
    /** Prefer the file id (1920px watermark-free jpg), then the notes_pre_post rewrite, then the raw URL. */
    static String xhsImage(String raw, String fileId) {
        if (fileId != null && fileId.matches("[A-Za-z0-9_-]{8,200}"))
            return "https://sns-img-qc.xhscdn.com/" + fileId + "?imageView2/2/w/1920/format/jpg";
        String url = raw == null ? "" : raw.replace("\\u002F", "/").strip();
        if (url.isBlank()) return "";
        if (url.contains("/notes_pre_post/")) {
            String imageId = url.substring(url.lastIndexOf('/') + 1).split("!", 2)[0];
            if (imageId.matches("[A-Za-z0-9_-]{8,200}"))
                return "https://ci.xiaohongshu.com/notes_pre_post/" + (url.contains("/spectrum/") ? "spectrum/" : "") + imageId + "?imageView2/format/jpg";
        }
        return url.replaceAll("![^?]*", "");
    }
    /** The platform answers stale or removed shares with /404 or /login instead of the note state. */
    static ApiException expiredOrUnavailable(URI finalUri, ApiException failure) {
        String value = finalUri == null ? "" : finalUri.toString();
        if (value.contains("error_code=300031")) return new ApiException(ApiError.MEDIA_PLATFORM_RESTRICTED);
        if (value.contains("/404") || value.contains("undertake_note_error"))
            return new ApiException(ApiError.MEDIA_LINK_EXPIRED);
        return failure == null ? new ApiException(ApiError.MEDIA_UNAVAILABLE) : failure;
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
    private static JsonNode matchingItem(JsonNode root, String id) {
        var lists = new ArrayList<JsonNode>();
        lists.add(root.path("aweme_details"));
        for (JsonNode loader : root.path("loaderData")) lists.add(loader.at("/videoInfoRes/item_list"));
        for (JsonNode list : lists) for (JsonNode item : list) {
            String actual = item.path("aweme_id").asText("");
            if ((actual.isEmpty() || actual.equals(id)) && hasByteDanceMedia(item)) return item;
        }
        return null;
    }
    static JsonNode byteDanceImages(JsonNode item) {
        for (String path : List.of("/image_post_info/images", "/image_post_info/image_list", "/images", "/image_list", "/image_infos", "/original_images")) {
            JsonNode images = item.at(path);
            if (images.isArray() && !images.isEmpty()) return images;
        }
        return item.path("images");
    }
    private static boolean hasByteDanceMedia(JsonNode item) {
        return !byteDanceImages(item).isEmpty() || !firstUrl(item.at("/video/play_addr/url_list")).isBlank()
                || !item.at("/video/bit_rate").isEmpty();
    }
    private static void fatalFallback(ApiException error, long deadline) {
        if (error.error() == ApiError.MEDIA_TOO_LARGE || error.error() == ApiError.INVALID_ARGUMENT) throw error;
        MediaHttp.checkDeadline(deadline);
    }
    static ArrayList<String> urls(JsonNode node) {
        var result = new ArrayList<String>();
        if (node.isTextual() && !node.asText().isBlank()) result.add(node.asText());
        else if (node.isArray()) for (JsonNode row : node) {
            String value = row.isTextual() ? row.asText() : row.path("url").asText(row.path("src").asText(""));
            if (!value.isBlank()) result.add(value);
            if (result.size() >= 8) break;
        }
        return result;
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
    private static void addUrls(List<Candidate> list, MediaInput input, String kind, String label, List<String> urls, int width, int height, long bitrate, String source) {
        if (urls.isEmpty()) throw unavailable();
        var owned = urls.stream().limit(8).map(input.platform()::mediaUri).distinct().toList();
        int before = list.size();
        add(list, input, kind, label, owned.get(0).toString(), width, height, bitrate, source);
        if (list.size() > before) {
            Candidate asset = list.remove(list.size() - 1);
            list.add(new Candidate(asset.kind(), asset.label(), asset.uri(), asset.width(), asset.height(), asset.bitrate(), asset.source(), owned.subList(1, Math.min(4, owned.size()))));
        }
    }
    private static void addCover(List<Candidate> list, MediaInput input, String url) {
        addCover(list, input, url.isBlank() ? List.of() : List.of(url));
    }
    private static void addCover(List<Candidate> list, MediaInput input, List<String> urls) {
        if (urls.isEmpty()) {
            var first = list.stream().filter(a -> a.kind().equals("IMAGE")).findFirst();
            if (first.isEmpty()) return;
            var addresses = new ArrayList<String>();
            addresses.add(first.get().uri().toString());
            first.get().fallbacks().forEach(uri -> addresses.add(uri.toString()));
            urls = addresses;
        }
        addUrls(list, input, "COVER", "", urls, 0, 0, 0, "UNKNOWN");
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
