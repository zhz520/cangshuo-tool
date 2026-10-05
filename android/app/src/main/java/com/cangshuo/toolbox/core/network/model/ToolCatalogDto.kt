package com.cangshuo.toolbox.core.network.model

import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.model.ToolStatus

/** Wire field names match GET /api/v1/tools; transport validates types before constructing this DTO. */
data class ToolCatalogDto(
    val code: String,
    val name: String,
    val description: String,
    val categoryCode: String,
    val icon: String?,
    val keywords: List<String>,
    val mode: String,
    val requiresLogin: Boolean,
    val status: String,
    val version: Int,
    val sortOrder: Int,
    val isFeatured: Boolean,
) {
    /** Reject unknown or invalid metadata; never turn a catalog entry into executable code. */
    fun toMetadataOrNull(): ToolMetadata? {
        val category = ToolCategory.fromCode(categoryCode) ?: return null
        val toolMode = ToolMode.entries.firstOrNull { it.name == mode } ?: return null
        val toolStatus = ToolStatus.entries.firstOrNull { it.name == status } ?: return null
        return try {
            ToolMetadata(
                code = code,
                name = name,
                description = description,
                category = category,
                mode = toolMode,
                icon = icon,
                keywords = keywords.toList(),
                requiresLogin = requiresLogin,
                status = toolStatus,
                version = version,
                sortOrder = sortOrder,
                isFeatured = isFeatured,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
