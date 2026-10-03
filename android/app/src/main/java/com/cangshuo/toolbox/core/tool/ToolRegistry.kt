package com.cangshuo.toolbox.core.tool

import android.content.Context
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolStatus
import java.util.Collections

/** A fixed collection of trusted tool implementations supplied by the app. */
class ToolRegistry(definitions: Collection<ToolDefinition>) {
    /** All registered definitions, including disabled tools, ordered by sortOrder and code. */
    val tools: List<ToolDefinition>

    private val toolsByCode: Map<String, ToolDefinition>

    init {
        val registered = LinkedHashMap<String, ToolDefinition>()
        for (definition in definitions) {
            val code = definition.metadata.code
            require(definition.code == code) { "Tool code must match its metadata" }
            require(!registered.containsKey(code)) { "Duplicate tool code" }
            registered[code] = definition
        }

        toolsByCode = registered.toMap()
        tools = Collections.unmodifiableList(
            registered.values.sortedWith(
                compareBy<ToolDefinition> { it.metadata.sortOrder }.thenBy { it.metadata.code },
            ),
        )
    }

    /** Exact code lookup; callers must still check status and device support before opening. */
    fun find(code: String): ToolDefinition? = toolsByCode[code]

    /** Lists enabled tools, retaining unsupported devices' entries for explicit UI feedback. */
    fun enabledTools(category: ToolCategory? = null): List<ToolDefinition> =
        Collections.unmodifiableList(
            tools.filter {
                it.metadata.status == ToolStatus.ENABLED &&
                    (category == null || it.metadata.category == category)
            },
        )

    fun featuredTools(category: ToolCategory? = null): List<ToolDefinition> =
        Collections.unmodifiableList(enabledTools(category).filter { it.metadata.isFeatured })

    /** Checks catalog status before device support; never opens a screen or requests permissions. */
    fun resolve(code: String, context: Context): ToolLookupResult {
        val definition = find(code) ?: return ToolLookupResult.NotFound
        return when (definition.metadata.status) {
            ToolStatus.DISABLED -> ToolLookupResult.Disabled
            ToolStatus.MAINTENANCE -> ToolLookupResult.Maintenance
            ToolStatus.ENABLED -> if (definition.isAvailable(context)) {
                ToolLookupResult.Available(definition)
            } else {
                ToolLookupResult.Unsupported
            }
        }
    }
}
