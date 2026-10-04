package com.cangshuo.toolbox.core.tool

import android.content.Context
import androidx.compose.runtime.Composable
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.model.ToolStatus

/** An executable implementation shipped in the app, backed by a single metadata source. */
interface ToolDefinition {
    val metadata: ToolMetadata

    val code: String
        get() = metadata.code

    val name: String
        get() = metadata.name

    val description: String
        get() = metadata.description

    val category: ToolCategory
        get() = metadata.category

    val icon: String?
        get() = metadata.icon

    val keywords: List<String>
        get() = metadata.keywords

    val mode: ToolMode
        get() = metadata.mode

    val requiresLogin: Boolean
        get() = metadata.requiresLogin

    val status: ToolStatus
        get() = metadata.status

    val version: Int
        get() = metadata.version

    val sortOrder: Int
        get() = metadata.sortOrder

    val isFeatured: Boolean
        get() = metadata.isFeatured

    /** Declared by trusted app code; request these only when the user opens the tool. */
    val requiredPermissions: List<String>

    /** Checks device support without requesting permissions or doing network/file work. */
    fun isAvailable(context: Context): Boolean

    /** Business logic and data access remain in the feature's ViewModel/UseCase/Repository. */
    @Composable
    fun Screen()
}
