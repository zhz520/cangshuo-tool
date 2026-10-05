package com.cangshuo.toolbox.admin.model;

import java.time.Instant;

public record AdminCategoryRow(long id, String code, String name, String icon, String description, int sortOrder,
    boolean enabled, int toolCount, Instant updatedAt) { }
