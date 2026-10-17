package com.cangshuo.toolbox.feature.calendar

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.calendar.ui.CalendarRoute

class LunarCalendarToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "lunar_calendar",
            name = resources.getString(R.string.calendar_name),
            description = resources.getString(R.string.calendar_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "lunar_calendar",
            keywords = resources.getStringArray(R.array.calendar_keywords).toList(),
            sortOrder = 623,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = CalendarRoute(factory)
}
