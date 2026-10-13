package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.ratelimit.RateLimitService;
import com.cangshuo.toolbox.tool.repository.ToolUsageRepository;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.zip.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MediaService {
    public static final String CODE = "short_video_parse";
    static final int MAX_SESSIONS = 100, RESULT_SECONDS = 900, MAX_ZIP_BYTES = 32 * 1024 * 1024;
    private static final String BASE = "/api/v1/web-tools/" + CODE;
    private final MediaParser parser;
    private final MediaHttp http;
    private final ToolUsageRepository catalog;
    private final RateLimitService limiter;
    private final boolean enabled;
    private final Clock clock;
    private final MediaParseCache parseCache;
    private final Semaphore parsing = new Semaphore(3), transfers = new Semaphore(8), archives = new Semaphore(1);
    private final Map<String, Session> sessions = new LinkedHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public MediaService(MediaParser parser, MediaHttp http, ToolUsageRepository catalog, RateLimitService limiter,
                        @Value("${toolbox.media.enabled:true}") boolean enabled) {
        this(parser, http, catalog, limiter, enabled, Clock.systemUTC());
    }
    MediaService(MediaParser parser, MediaHttp http, ToolUsageRepository catalog, RateLimitService limiter, boolean enabled, Clock clock) {
        this.parser = parser; this.http = http; this.catalog = catalog; this.limiter = limiter; this.enabled = enabled; this.clock = clock;
        this.parseCache = new MediaParseCache(clock);
    }
    public record PlatformInfo(String code, String nameZh, String nameEn, List<String> hosts) {}
    public record Capabilities(boolean available, List<PlatformInfo> platforms, int maxTextLength, int maxImages,
                               long maxFileBytes, int maxZipBytes, int resultSeconds, int timeoutSeconds) {}
    public Capabilities capabilities() {
        return new Capabilities(enabled && catalog.visible(CODE), Arrays.stream(MediaPlatform.values())
                .map(p -> new PlatformInfo(p.code(), p.nameZh(), p.nameEn(), p.shareHosts())).toList(),
                4096, 50, MediaHttp.MAX_FILE_BYTES, MAX_ZIP_BYTES, RESULT_SECONDS, 30);
    }
    public record Asset(int index, String kind, String label, int width, int height, long bitrate, String source,
                        String previewPath, String downloadPath, String directUrl) {}
    public record Result(String id, String platform, String platformName, String sourceUrl, String title,
                         String description, String author, long durationSeconds, Instant expiresAt,
                         List<Asset> assets, String albumPath) {}
    private record Session(MediaParser.Parsed media, Instant expiresAt) {}

    public Result parse(String text, String clientIdentity) {
        ready();
        MediaInput input = MediaInput.parse(text);
        if (!limiter.check("toolbox:rate:media:parse:" + clientIdentity, 12, 60).allowed()) throw new ApiException(ApiError.TOO_MANY_REQUESTS);
        MediaParser.Parsed parsed = parseCache.load(input.platform().code() + ":" + input.uri().toASCIIString(), () -> {
            if (!parsing.tryAcquire()) throw new ApiException(ApiError.SERVICE_UNAVAILABLE);
            try { return parser.parse(input); }
            finally { parsing.release(); }
        });
        String id = UUID.randomUUID().toString().replace("-", "");
        Instant expires = clock.instant().plusSeconds(RESULT_SECONDS);
        synchronized (sessions) {
            prune();
            while (sessions.size() >= MAX_SESSIONS) sessions.remove(sessions.keySet().iterator().next());
            sessions.put(id, new Session(parsed, expires));
        }
        var assets = new ArrayList<Asset>();
        for (int i = 0; i < parsed.assets().size(); i++) {
            var source = parsed.assets().get(i);
            String path = BASE + "/assets/" + id + "/" + i;
            assets.add(new Asset(i, source.kind(), source.label(), source.width(), source.height(), source.bitrate(),
                    source.source(), path, path + "?download=true", source.uri().toString()));
        }
        return new Result(id, parsed.platform().code(), parsed.platform().nameZh(), parsed.sourceUrl().toString(),
                parsed.title(), parsed.description(), parsed.author(), parsed.durationSeconds(), expires,
                List.copyOf(assets), BASE + "/albums/" + id);
    }
    public PreparedTransfer transfer(String id, int index, String range) {
        ready();
        Session session = session(id);
        if (index < 0 || index >= session.media().assets().size()) throw new ApiException(ApiError.INVALID_ARGUMENT);
        validateRange(range);
        if (!transfers.tryAcquire()) throw new ApiException(ApiError.SERVICE_UNAVAILABLE);
        try {
            var asset = session.media().assets().get(index);
            var download = openAsset(session.media().platform(), asset, range, MediaHttp.deadline(150));
            return new PreparedTransfer(download, session.media().platform().code() + "_" + asset.kind().toLowerCase(Locale.ROOT)
                    + "_" + (index + 1) + "." + download.extension(), transfers);
        } catch (ApiException error) { transfers.release(); throw error; }
        catch (IOException error) { transfers.release(); throw new ApiException(ApiError.MEDIA_PROVIDER_ERROR); }
        catch (RuntimeException error) { transfers.release(); throw new ApiException(ApiError.MEDIA_PROVIDER_ERROR); }
    }
    public record PreparedTransfer(MediaHttp.Download download, String fileName, Semaphore slots,
                                   java.util.concurrent.atomic.AtomicBoolean closed) implements AutoCloseable {
        public PreparedTransfer(MediaHttp.Download download, String fileName, Semaphore slots) {
            this(download, fileName, slots, new java.util.concurrent.atomic.AtomicBoolean());
        }
        @Override public void close() throws IOException { if (closed.compareAndSet(false, true)) try { download.close(); } finally { slots.release(); } }
    }
    /** Build a bounded complete archive before returning 200; failed/oversized archives never masquerade as success. */
    public PreparedArchive album(String id, String selection, String clientIdentity) {
        ready();
        Session session = session(id);
        List<Integer> indices = selections(selection, session.media());
        if (!limiter.check("toolbox:rate:media:zip:" + clientIdentity, 3, 60).allowed()) throw new ApiException(ApiError.TOO_MANY_REQUESTS);
        if (!archives.tryAcquire()) throw new ApiException(ApiError.SERVICE_UNAVAILABLE);
        try {
            long deadline = MediaHttp.deadline(45);
            var bytes = new ByteArrayOutputStream();
            long rawBytes = 0;
            try (var zip = new ZipOutputStream(new FilterOutputStream(bytes) {
                @Override public void write(byte[] b, int off, int len) throws IOException {
                    if ((long) bytes.size() + len > MAX_ZIP_BYTES) throw new ApiException(ApiError.MEDIA_TOO_LARGE);
                    out.write(b, off, len);
                }
                @Override public void write(int b) throws IOException {
                    if (bytes.size() >= MAX_ZIP_BYTES) throw new ApiException(ApiError.MEDIA_TOO_LARGE);
                    out.write(b);
                }
            })) {
                int position = 0;
                for (int index : indices) {
                    var asset = session.media().assets().get(index);
                    try (var download = openAsset(session.media().platform(), asset, null, deadline)) {
                        if (!download.contentType().startsWith("image/")) throw new ApiException(ApiError.MEDIA_FORMAT_UNSUPPORTED);
                        if (download.length() > MAX_ZIP_BYTES - rawBytes) throw new ApiException(ApiError.MEDIA_TOO_LARGE);
                        byte[] image = MediaHttp.readLimited(download.input(), (int) (MAX_ZIP_BYTES - rawBytes), deadline);
                        if (download.length() >= 0 && image.length != download.length()) throw new ApiException(ApiError.MEDIA_PROVIDER_ERROR);
                        rawBytes += image.length;
                        zip.putNextEntry(new ZipEntry(String.format(Locale.ROOT, "%03d.%s", ++position, download.extension())));
                        zip.write(image); zip.closeEntry();
                    }
                }
            }
            return new PreparedArchive(bytes.toByteArray(), session.media().platform().code() + "_album.zip", archives);
        } catch (ApiException error) { archives.release(); throw error; }
        catch (Exception error) { archives.release(); throw new ApiException(ApiError.MEDIA_PROVIDER_ERROR); }
    }
    public record PreparedArchive(byte[] bytes, String fileName, Semaphore slots,
                                  java.util.concurrent.atomic.AtomicBoolean closed) implements AutoCloseable {
        public PreparedArchive(byte[] bytes, String fileName, Semaphore slots) {
            this(bytes, fileName, slots, new java.util.concurrent.atomic.AtomicBoolean());
        }
        @Override public void close() { if (closed.compareAndSet(false, true)) slots.release(); }
    }
    private MediaHttp.Download openAsset(MediaPlatform platform, MediaParser.Candidate asset, String range, long deadline) throws IOException {
        var candidates = new ArrayList<java.net.URI>();
        candidates.add(asset.uri()); candidates.addAll(asset.fallbacks());
        ApiException failure = null;
        for (var uri : candidates) {
            MediaHttp.checkDeadline(deadline);
            try {
                var source = http.open(platform, uri, range, deadline);
                String expected = List.of("VIDEO", "LIVE").contains(asset.kind()) ? "video/" : "image/";
                if (!source.contentType().startsWith(expected)) {
                    source.close(); throw new ApiException(ApiError.MEDIA_FORMAT_UNSUPPORTED);
                }
                return source;
            } catch (ApiException error) {
                if (error.error() == ApiError.INVALID_ARGUMENT || error.error() == ApiError.MEDIA_TOO_LARGE) throw error;
                failure = error;
            }
        }
        throw failure == null ? new ApiException(ApiError.MEDIA_PROVIDER_ERROR) : failure;
    }
    static List<Integer> selections(String selection, MediaParser.Parsed media) {
        if (selection == null || !selection.matches("[0-9]{1,3}(?:,[0-9]{1,3}){0,49}")) throw new ApiException(ApiError.INVALID_ARGUMENT);
        Set<Integer> indices = new TreeSet<>();
        for (String raw : selection.split(",")) {
            int index = Integer.parseInt(raw);
            if (index >= media.assets().size() || !media.assets().get(index).kind().equals("IMAGE") || !indices.add(index))
                throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
        return List.copyOf(indices);
    }
    static void validateRange(String range) {
        if (range == null) return;
        if (!range.matches("bytes=[0-9]{1,12}-[0-9]{0,12}")) throw new ApiException(ApiError.INVALID_ARGUMENT);
        String[] parts = range.substring(6).split("-", -1);
        long start = Long.parseLong(parts[0]);
        if (start >= MediaHttp.MAX_FILE_BYTES || !parts[1].isEmpty()
                && (Long.parseLong(parts[1]) < start || Long.parseLong(parts[1]) >= MediaHttp.MAX_FILE_BYTES))
            throw new ApiException(ApiError.INVALID_ARGUMENT);
    }
    public void clear(String id) { if (id != null && id.matches("[a-f0-9]{32}")) synchronized (sessions) { sessions.remove(id); } }
    private Session session(String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) throw new ApiException(ApiError.INVALID_ARGUMENT);
        synchronized (sessions) {
            prune();
            Session value = sessions.get(id);
            if (value == null) throw new ApiException(ApiError.MEDIA_RESULT_EXPIRED);
            return value;
        }
    }
    private void prune() { sessions.values().removeIf(value -> !value.expiresAt().isAfter(clock.instant())); }
    private void ready() { if (!enabled || !catalog.visible(CODE)) throw new ApiException(ApiError.SERVICE_UNAVAILABLE); }
}
