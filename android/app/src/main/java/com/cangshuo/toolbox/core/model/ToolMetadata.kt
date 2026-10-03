package com.cangshuo.toolbox.core.model

private val toolCodePattern = Regex("[a-z][a-z0-9_]{0,63}")
private val iconNamePattern = Regex("[a-z][a-z0-9_]{0,127}")

/** Catalog data only. Android permissions and executable screens belong to ToolDefinition. */
data class ToolMetadata(
    val code: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val mode: ToolMode,
    val icon: String? = null,
    val keywords: List<String> = emptyList(),
    val requiresLogin: Boolean = false,
    val status: ToolStatus = ToolStatus.ENABLED,
    val version: Int = 1,
    val sortOrder: Int = 0,
    val isFeatured: Boolean = false,
) {
    val categoryCode: String
        get() = category.code

    init {
        require(toolCodePattern.matches(code)) { "Invalid tool code" }
        require(name.isNotBlank() && name.length <= 128) { "Invalid tool name" }
        require(description.length <= 500) { "Tool description is too long" }
        require(icon == null || iconNamePattern.matches(icon)) { "Invalid tool icon name" }
        require(keywords.all { it.isNotBlank() }) { "Tool keywords must not be blank" }
        require(version >= 1) { "Tool metadata version must be positive" }
    }
}
