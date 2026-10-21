package com.cangshuo.toolbox.feature.fps

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.fps.ui.FpsRoute

class FpsToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata(code = "frame_rate_test", name = resources.getString(R.string.fps_name),
        description = resources.getString(R.string.fps_description), category = ToolCategory.OTHER, mode = ToolMode.LOCAL,
        icon = "frame_rate_test", keywords = resources.getStringArray(R.array.fps_keywords).toList(), sortOrder = 643, isFeatured = false)
    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = FpsRoute(factory)
}
