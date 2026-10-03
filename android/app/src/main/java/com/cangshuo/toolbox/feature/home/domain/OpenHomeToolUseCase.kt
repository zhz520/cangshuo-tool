package com.cangshuo.toolbox.feature.home.domain

import android.content.Context
import android.content.pm.PackageManager
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.core.tool.ToolLookupResult

sealed interface HomeToolResult {
    data class Opened(val tool: ToolDefinition) : HomeToolResult
    data object NotFound : HomeToolResult
    data object Disabled : HomeToolResult
    data object Maintenance : HomeToolResult
    data object Unsupported : HomeToolResult
    data object LoginRequired : HomeToolResult
    data object PermissionRequired : HomeToolResult
}

/** Current visitor flow: permission requests and authentication belong to their feature tasks. */
class OpenHomeToolUseCase(private val repository: HomeRepository) {
    operator fun invoke(code: String, context: Context): HomeToolResult =
        when (val result = repository.resolveTool(code, context)) {
            is ToolLookupResult.Available -> when {
                result.tool.metadata.requiresLogin -> HomeToolResult.LoginRequired
                result.tool.requiredPermissions.any {
                    context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                } -> HomeToolResult.PermissionRequired
                else -> HomeToolResult.Opened(result.tool)
            }
            ToolLookupResult.NotFound -> HomeToolResult.NotFound
            ToolLookupResult.Disabled -> HomeToolResult.Disabled
            ToolLookupResult.Maintenance -> HomeToolResult.Maintenance
            ToolLookupResult.Unsupported -> HomeToolResult.Unsupported
        }
}
