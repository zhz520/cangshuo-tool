package com.cangshuo.toolbox.tool.model;

/** Internal read model. Database JSON is parsed by the service before reaching the API. */
public record ToolCatalogEntry(
        String code,
        String name,
        String description,
        String categoryCode,
        String icon,
        String keywordsJson,
        String mode,
        boolean requiresLogin,
        String status,
        int version,
        int sortOrder,
        boolean featured) {
}
