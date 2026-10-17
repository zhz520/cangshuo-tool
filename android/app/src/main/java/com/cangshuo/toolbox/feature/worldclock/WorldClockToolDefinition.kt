package com.cangshuo.toolbox.feature.worldclock

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.worldclock.ui.WorldClockRoute

class WorldClockToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "world_clock",
            name = resources.getString(R.string.world_clock_name),
            description = resources.getString(R.string.world_clock_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "world_clock",
            keywords = resources.getStringArray(R.array.world_clock_keywords).toList(),
            sortOrder = 621,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = WorldClockRoute(factory)
}
