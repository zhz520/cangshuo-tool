package com.cangshuo.toolbox.admin.model;

/** Only keys and metadata; synced payloads stay private. */
public record AdminUserSyncEntryResponse(String entityType, String entityKey, long updatedAtMs, boolean deleted) { }
