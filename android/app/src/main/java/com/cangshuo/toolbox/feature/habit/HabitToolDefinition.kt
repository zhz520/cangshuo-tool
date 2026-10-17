package com.cangshuo.toolbox.feature.habit

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.habit.ui.HabitRoute

class HabitToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "habit_tracker",
            name = resources.getString(R.string.habit_name),
            description = resources.getString(R.string.habit_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "habit_tracker",
            keywords = resources.getStringArray(R.array.habit_keywords).toList(),
            sortOrder = 630,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = HabitRoute(factory)
}
