package com.cangshuo.toolbox.feature.timescreen

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.timescreen.ui.TimeScreenRoute

class TimeScreenToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "time_screen",
            name = resources.getString(R.string.time_screen_name),
            description = resources.getString(R.string.time_screen_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "time_screen",
            keywords = resources.getStringArray(R.array.time_screen_keywords).toList(),
            sortOrder = 626,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = TimeScreenRoute(factory)
}
