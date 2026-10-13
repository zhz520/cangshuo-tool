package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import java.net.*;
import java.util.*;

/** Host ownership is checked with label boundaries, never a substring of user input. */
public enum MediaPlatform {
    DOUYIN("抖音", "Douyin", "https://www.douyin.com/",
            List.of("v.douyin.com", "www.douyin.com", "www.iesdouyin.com", "iesdouyin.com"),
            // snssdk.com hosts the fallback mobile feed node used when the primary node misses a work.
            List.of("douyin.com", "iesdouyin.com", "amemv.com", "snssdk.com"),
            List.of("douyinvod.com", "bytecdn.cn", "byteimg.com", "douyinpic.com", "douyinstatic.com", "pstatp.com", "snssdk.com", "amemv.com", "ibytedtos.com", "bytedance.com", "bytegecko.com", "365yg.com", "toutiaoimg.com", "zjcdn.com")),
    KUAISHOU("快手", "Kuaishou", "https://www.kuaishou.com/",
            List.of("v.kuaishou.com", "www.kuaishou.com", "www.gifshow.com", "v.m.chenzhongtech.com"),
            List.of("kuaishou.com", "gifshow.com", "chenzhongtech.com"),
            List.of("kwaicdn.com", "ksapisrv.com", "gifshow.com", "ksyuncdn.com", "yximgs.com", "kwai.net", "kuaishou.com")),
    XIAOHONGSHU("小红书", "Xiaohongshu", "https://www.xiaohongshu.com/",
            List.of("www.xiaohongshu.com", "xiaohongshu.com", "xhslink.com", "xhslink.cn"),
            List.of("xiaohongshu.com", "xhslink.com", "xhslink.cn"),
            List.of("xhscdn.com", "xiaohongshu.com", "xhsimg.com")),
    BILIBILI("哔哩哔哩", "Bilibili", "https://www.bilibili.com/",
            List.of("www.bilibili.com", "m.bilibili.com", "bilibili.com", "b23.tv"),
            List.of("bilibili.com", "b23.tv"),
            List.of("bilivideo.com", "bilivideo.cn", "bilivideo.net", "hdslb.com")),
    WEIBO("微博", "Weibo", "https://weibo.com/",
            List.of("weibo.com", "www.weibo.com", "m.weibo.cn", "weibo.cn", "h5.video.weibo.com", "t.cn"),
            List.of("weibo.com", "weibo.cn", "t.cn"),
            List.of("weibocdn.com", "sinaimg.cn", "sinaimg.com", "sina.com.cn")),
    XIGUA("西瓜视频", "Xigua", "https://www.ixigua.com/",
            List.of("www.ixigua.com", "v.ixigua.com", "m.ixigua.com", "ixigua.com"),
            List.of("ixigua.com", "iesdouyin.com"),
            List.of("ixiguavideo.com", "douyinvod.com", "bytecdn.cn", "byteimg.com", "pstatp.com", "snssdk.com", "365yg.com", "toutiaoimg.com", "zjcdn.com")),
    PIPIXIA("皮皮虾", "Pipixia", "https://h5.pipix.com/",
            List.of("h5.pipix.com", "pipix.com"), List.of("pipix.com"),
            List.of("pstatp.com", "byteimg.com", "bytecdn.cn", "snssdk.com", "pipix.com", "ibytedtos.com", "365yg.com", "toutiaoimg.com")),
    TIKTOK("TikTok", "TikTok", "https://www.tiktok.com/",
            List.of("www.tiktok.com", "tiktok.com", "vm.tiktok.com", "vt.tiktok.com"), List.of("tiktok.com"),
            List.of("tiktokcdn.com", "tiktokcdn-us.com", "tiktokv.com", "tiktokv.us", "tiktok.com", "byteoversea.com", "ibytedtos.com", "muscdn.com", "musical.ly"));

    private final String nameZh, nameEn, referer;
    private final List<String> shares, pages, media;
    MediaPlatform(String zh, String en, String referer, List<String> shares, List<String> pages, List<String> media) {
        this.nameZh = zh; this.nameEn = en; this.referer = referer;
        this.shares = shares; this.pages = pages; this.media = media;
    }
    public String code() { return name().toLowerCase(Locale.ROOT); }
    public String nameZh() { return nameZh; }
    public String nameEn() { return nameEn; }
    public String referer() { return referer; }
    public List<String> shareHosts() { return shares; }

    public static MediaPlatform identify(URI uri) {
        validate(uri);
        return Arrays.stream(values()).filter(p -> p.shares.contains(uri.getHost().toLowerCase(Locale.ROOT)))
                .findFirst().orElseThrow(() -> new ApiException(ApiError.MEDIA_UNSUPPORTED_PLATFORM));
    }
    public void validatePage(URI uri) { validateOwned(uri, pages); }
    public void validateMedia(URI uri) {
        validateOwned(uri, media);
        if (uri.getPath() != null && uri.getPath().toLowerCase(Locale.ROOT).matches(".*\\.(?:m3u8|mpd)(?:/.*)?$"))
            throw new ApiException(ApiError.MEDIA_FORMAT_UNSUPPORTED);
    }
    private static void validateOwned(URI uri, List<String> roots) {
        validate(uri);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (roots.stream().noneMatch(root -> host.equals(root) || host.endsWith("." + root)))
            throw new ApiException(ApiError.MEDIA_PROVIDER_ERROR);
    }
    static void validate(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getRawFragment() != null
                || uri.getPort() != -1 && uri.getPort() != 443 || uri.toASCIIString().length() > 8192
                || !uri.getHost().matches("(?i)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")
                || uri.getHost().endsWith(".") || uri.getHost().contains(".."))
            throw new ApiException(ApiError.INVALID_ARGUMENT);
    }
    /** Metadata may contain protocol-relative or legacy HTTP links to owned CDN hosts. */
    public URI mediaUri(String raw) {
        try {
            String value = raw == null ? "" : raw.strip();
            if (value.startsWith("//")) value = "https:" + value;
            if (value.regionMatches(true, 0, "http://", 0, 7)) value = "https://" + value.substring(7);
            URI uri = URI.create(value);
            validateMedia(uri);
            return uri;
        } catch (ApiException invalid) {
            if (invalid.error() == ApiError.MEDIA_FORMAT_UNSUPPORTED) throw invalid;
            throw new ApiException(ApiError.MEDIA_PROVIDER_ERROR);
        } catch (IllegalArgumentException invalid) { throw new ApiException(ApiError.MEDIA_PROVIDER_ERROR); }
    }
}
