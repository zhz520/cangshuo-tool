package com.cangshuo.toolbox.feature.vibrator

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.vibrator.ui.VibratorRoute

class VibratorToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata(code = "vibrator_patterns", name = resources.getString(R.string.vibrator_name),
        description = resources.getString(R.string.vibrator_description), category = ToolCategory.OTHER, mode = ToolMode.LOCAL,
        icon = "vibrator_patterns", keywords = resources.getStringArray(R.array.vibrator_keywords).toList(), sortOrder = 641, isFeatured = false)
    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = VibratorRoute(factory)
}
