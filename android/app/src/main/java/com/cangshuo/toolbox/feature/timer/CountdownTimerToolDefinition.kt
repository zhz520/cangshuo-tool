package com.cangshuo.toolbox.feature.timer

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.timer.ui.TimerRoute

class CountdownTimerToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "countdown_timer",
            name = resources.getString(R.string.countdown_name),
            description = resources.getString(R.string.countdown_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "countdown_timer",
            keywords = resources.getStringArray(R.array.countdown_keywords).toList(),
            sortOrder = 637,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = TimerRoute(factory)
}
