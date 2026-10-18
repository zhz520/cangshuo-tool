package com.cangshuo.toolbox.feature.pomodoro

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.pomodoro.ui.PomodoroRoute

class PomodoroToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "pomodoro_focus",
            name = resources.getString(R.string.pomodoro_name),
            description = resources.getString(R.string.pomodoro_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "pomodoro_focus",
            keywords = resources.getStringArray(R.array.pomodoro_keywords).toList(),
            sortOrder = 632,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true

    @Composable
    override fun Screen() = PomodoroRoute(factory)
}
