package com.cangshuo.toolbox.sync.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class SyncModels {
    private SyncModels() { }
    public record Payload(Long lastUsedAt, Long useCount, String value) { }
    public record Item(@NotBlank @Size(max=16) String entityType, @NotBlank @Size(max=64) String entityKey,
                       @NotNull Long updatedAt, @NotNull Boolean deleted, @NotNull @Valid Payload payload) {
        @Override public String toString() { return "SyncItem[redacted]"; }
    }
    public record Entry(String entityType, String entityKey, long updatedAt, String deviceId,
                        boolean deleted, Payload payload, long revision) {
        @Override public String toString() { return "SyncEntry[redacted]"; }
    }
    public record Push(@NotBlank @Pattern(regexp="[0-9a-f]{32}") String deviceId,
                       @NotNull @Size(min=1,max=100) List<@NotNull @Valid Item> items) {
        @Override public String toString() { return "SyncPush[redacted]"; }
    }
    public record PushResult(int applied, String nextCursor) { }
    public record PullResult(List<Entry> items, String nextCursor, boolean hasMore) { }
}
