package com.cangshuo.toolbox.tool.model;

/** Aggregated usage row joined with visible catalog metadata. */
public record PopularToolRow(String code, String name, String description, String icon, long useCount) {
}
