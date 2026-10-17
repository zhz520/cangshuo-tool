package com.cangshuo.toolbox.feature.todo

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.todo.ui.TodoRoute

class TodoToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "todo_list",
            name = resources.getString(R.string.todo_name),
            description = resources.getString(R.string.todo_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "todo_list",
            keywords = resources.getStringArray(R.array.todo_keywords).toList(),
            sortOrder = 629,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = TodoRoute(factory)
}
