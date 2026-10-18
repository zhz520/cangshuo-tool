package com.cangshuo.toolbox.feature.period

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.period.ui.PeriodRoute

class PeriodTrackerToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "period_tracker",
            name = resources.getString(R.string.period_name),
            description = resources.getString(R.string.period_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "period_tracker",
            keywords = resources.getStringArray(R.array.period_keywords).toList(),
            sortOrder = 636,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = PeriodRoute(factory)
}
