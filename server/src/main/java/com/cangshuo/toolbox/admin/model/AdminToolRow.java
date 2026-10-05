package com.cangshuo.toolbox.admin.model;

import java.time.Instant;

/** Internal read model; JSON columns stay as raw text until the service verifies them. */
public record AdminToolRow(long id, String code, String name, String description, String categoryCode,
    String categoryName, String icon, String keywordsJson, String mode, boolean requiresLogin, String status,
    int version, int sortOrder, boolean featured, String configJson, Instant updatedAt) { }
