package com.cangshuo.toolbox.sitelink.model;

import java.time.Instant;

public record SiteLinkRow(long id, String title, String url, String description, String status,
        Long submittedBy, Instant createdAt, Instant updatedAt) {
}
