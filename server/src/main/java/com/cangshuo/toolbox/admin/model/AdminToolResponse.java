package com.cangshuo.toolbox.admin.model;

import java.time.Instant;
import java.util.List;

/** Administrator view of a catalog entry, including configuration JSON and timestamps. */
public record AdminToolResponse(long id, String code, String name, String description, String categoryCode,
    String categoryName, String icon, List<String> keywords, String mode, boolean requiresLogin, String status,
    int version, int sortOrder, boolean featured, String configJson, Instant updatedAt) {

    public AdminToolResponse {
        keywords = List.copyOf(keywords);
    }
}
