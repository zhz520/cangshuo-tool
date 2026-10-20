package com.cangshuo.toolbox.feature.tapspeed

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.tapspeed.ui.TapRoute

class TapSpeedToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata(code = "tap_speed", name = resources.getString(R.string.tap_name),
        description = resources.getString(R.string.tap_description), category = ToolCategory.OTHER, mode = ToolMode.LOCAL,
        icon = "tap_speed", keywords = resources.getStringArray(R.array.tap_keywords).toList(), sortOrder = 639, isFeatured = false)
    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = TapRoute(factory)
}
