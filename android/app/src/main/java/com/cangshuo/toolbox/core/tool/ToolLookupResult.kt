package com.cangshuo.toolbox.core.tool

sealed interface ToolLookupResult {
    /** Enabled and supported on this device; the open flow must also check login and permissions. */
    data class Available(val tool: ToolDefinition) : ToolLookupResult

    data object NotFound : ToolLookupResult

    data object Disabled : ToolLookupResult

    data object Maintenance : ToolLookupResult

    data object Unsupported : ToolLookupResult
}
