package com.cangshuo.toolbox.lanzou;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.ratelimit.RateLimitService;
import com.cangshuo.toolbox.tool.repository.ToolUsageRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LanzouService {
    public static final String CODE = "lanzou_direct";
    static final int CACHE_SECONDS = 180, RESULT_SECONDS = 300, MAX_CACHE = 100, REQUESTS_PER_MINUTE = 8;
    private final LanzouParser parser;
    private final ToolUsageRepository catalog;
    private final RateLimitService limiter;
    private final boolean enabled;
    private final Clock clock;
    private final Semaphore slots = new Semaphore(2);
    private final LinkedHashMap<String, Entry> cache = new LinkedHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Result>> inflight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> cooldown = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public LanzouService(LanzouParser parser, ToolUsageRepository catalog, RateLimitService limiter,
                         @Value("${toolbox.lanzou.enabled:true}") boolean enabled) {
        this(parser, catalog, limiter, enabled, Clock.systemUTC());
    }
    LanzouService(LanzouParser parser, ToolUsageRepository catalog, RateLimitService limiter, boolean enabled, Clock clock) {
        this.parser = parser; this.catalog = catalog; this.limiter = limiter; this.enabled = enabled; this.clock = clock;
    }

    public record Capabilities(boolean available, int maxTextLength, int maxPasswordLength, int cacheSeconds,
                               int requestsPerMinute, int maxConcurrent, String verificationMode) {}
    public record Result(String shareUrl, String fileName, String fileSize, String state, String directUrl,
                         String downloadPageUrl, boolean verificationRequired, boolean rateLimited, boolean cached,
                         Instant resolvedAt, int expiresInSeconds) {}
    private record Entry(Result value, Instant expiresAt) {}

    public Capabilities capabilities() {
        return new Capabilities(enabled && catalog.visible(CODE), 4096, 64, CACHE_SECONDS,
                REQUESTS_PER_MINUTE, 2, "OFFICIAL_PAGE_FALLBACK");
    }

    public Result parse(String text, String password, String identity) {
        ready();
        LanzouInput input = LanzouInput.parse(text, password);
        String client = identity == null || identity.isBlank() ? "unknown" : identity.substring(0, Math.min(64, identity.length()));
        if (!limiter.check("toolbox:rate:lanzou:parse:" + client, REQUESTS_PER_MINUTE, 60).allowed())
            throw new ApiException(ApiError.TOO_MANY_REQUESTS);
        Instant now = clock.instant();
        if (cooldown.getOrDefault(input.uri().getHost(), Instant.MIN).isAfter(now))
            throw new ApiException(ApiError.LANZOU_RESTRICTED);
        String key = key(input);
        Entry hit;
        synchronized (cache) {
            prune(now); hit = cache.get(key);
        }
        if (hit != null && hit.expiresAt().isAfter(now)) return cached(hit.value(), now);

        var created = new CompletableFuture<Result>();
        var existing = inflight.putIfAbsent(key, created);
        if (existing != null) return await(existing, now);
        try {
            if (!slots.tryAcquire()) throw new ApiException(ApiError.SERVICE_UNAVAILABLE);
            try {
                var parsed = parser.parse(input, System.nanoTime() + TimeUnit.SECONDS.toNanos(25));
                Result value = map(parsed, now);
                synchronized (cache) {
                    cache.put(key, new Entry(value, now.plusSeconds(CACHE_SECONDS)));
                    while (cache.size() > MAX_CACHE) cache.remove(cache.keySet().iterator().next());
                }
                created.complete(value);
                return value;
            } finally {
                slots.release();
            }
        } catch (RuntimeException error) {
            ApiError code = error instanceof ApiException api ? api.error() : ApiError.LANZOU_PROVIDER_ERROR;
            if (code == ApiError.LANZOU_VERIFICATION_REQUIRED) {
                Result fallback = new Result(input.uri().toString(), "", "", LanzouParser.State.OFFICIAL_PAGE.name(),
                        null, input.uri().toString(), true, false, false, now, CACHE_SECONDS);
                created.complete(fallback);
                return fallback;
            }
            if (code == ApiError.LANZOU_RESTRICTED || code == ApiError.LANZOU_TIMEOUT)
                cooldown.put(input.uri().getHost(), now.plusSeconds(60));
            created.completeExceptionally(error);
            throw error;
        } finally {
            inflight.remove(key, created);
        }
    }

    private Result await(CompletableFuture<Result> future, Instant now) {
        try { return cached(future.join(), now); }
        catch (CompletionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new ApiException(ApiError.LANZOU_PROVIDER_ERROR);
        }
    }
    private static Result cached(Result value, Instant now) {
        return new Result(value.shareUrl(), value.fileName(), value.fileSize(), value.state(), value.directUrl(),
                value.downloadPageUrl(), value.verificationRequired(), value.rateLimited(), true, now,
                Math.max(0, CACHE_SECONDS - (int) Math.max(0, Duration.between(value.resolvedAt(), now).toSeconds())));
    }
    private static Result map(LanzouParser.Parsed value, Instant now) {
        return new Result(value.shareUri().toString(), value.fileName(), value.fileSize(), value.state().name(),
                value.directUri() == null ? null : value.directUri().toString(), value.downloadPage().toString(),
                value.verificationRequired(), value.rateLimited(), false, now,
                CACHE_SECONDS);
    }
    private static String key(LanzouInput input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(input.uri().toASCIIString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(input.password().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    private void prune(Instant now) { cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now)); }
    private void ready() { if (!enabled || !catalog.visible(CODE)) throw new ApiException(ApiError.SERVICE_UNAVAILABLE); }
}
