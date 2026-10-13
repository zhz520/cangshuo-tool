package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Bounded per-link single-flight; no raw inputs or results are written to persistent storage. */
final class MediaParseCache {
    private record Entry(CompletableFuture<MediaParser.Parsed> future, Instant expiresAt) {}
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Clock clock;
    MediaParseCache(Clock clock) { this.clock = clock; }
    MediaParser.Parsed load(String key, Supplier<MediaParser.Parsed> loader) {
        Entry entry; boolean owner = false;
        synchronized (entries) {
            Instant now = clock.instant();
            entries.values().removeIf(e -> e.future().isDone() && !e.expiresAt().isAfter(now));
            entry = entries.get(key);
            if (entry == null) {
                if (entries.size() >= 100) {
                    var victim = entries.entrySet().stream().filter(e -> e.getValue().future().isDone()).findFirst();
                    if (victim.isEmpty()) throw new ApiException(ApiError.SERVICE_UNAVAILABLE);
                    entries.remove(victim.get().getKey());
                }
                entry = new Entry(new CompletableFuture<>(), now.plusSeconds(35));
                entries.put(key, entry); owner = true;
            }
        }
        if (owner) {
            try {
                var value = loader.get();
                synchronized (entries) { entries.put(key, new Entry(entry.future(), clock.instant().plusSeconds(180))); }
                entry.future().complete(value);
            } catch (Throwable error) {
                synchronized (entries) {
                    if (error instanceof ApiException api && Set.of(ApiError.MEDIA_UNAVAILABLE, ApiError.MEDIA_PROVIDER_ERROR,
                            ApiError.MEDIA_PLATFORM_RESTRICTED, ApiError.MEDIA_TIMEOUT).contains(api.error()))
                        entries.put(key, new Entry(entry.future(), clock.instant().plusSeconds(30)));
                    else entries.remove(key);
                }
                entry.future().completeExceptionally(error);
            }
        }
        try { return entry.future().get(35, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new ApiException(ApiError.MEDIA_TIMEOUT); }
        catch (TimeoutException error) { throw new ApiException(ApiError.MEDIA_TIMEOUT); }
        catch (ExecutionException error) {
            if (error.getCause() instanceof ApiException api) throw api;
            if (error.getCause() instanceof Error fatal) throw fatal;
            throw new ApiException(ApiError.MEDIA_PROVIDER_ERROR);
        }
    }
}
