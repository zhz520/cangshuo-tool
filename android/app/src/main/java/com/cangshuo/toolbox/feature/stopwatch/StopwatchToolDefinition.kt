package com.cangshuo.toolbox.feature.stopwatch

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.stopwatch.ui.StopwatchRoute

class StopwatchToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata("stopwatch", resources.getString(R.string.stopwatch_name), resources.getString(R.string.stopwatch_description),
        ToolCategory.LIFE, ToolMode.LOCAL, icon = "stopwatch", keywords = resources.getStringArray(R.array.stopwatch_keywords).toList(), sortOrder = 620)
    override val requiredPermissions = emptyList<String>()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = StopwatchRoute(factory)
}
