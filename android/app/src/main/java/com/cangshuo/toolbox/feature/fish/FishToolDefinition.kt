package com.cangshuo.toolbox.feature.fish

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.fish.ui.FishRoute

class FishToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata(code = "wooden_fish", name = resources.getString(R.string.fish_name),
        description = resources.getString(R.string.fish_description), category = ToolCategory.OTHER, mode = ToolMode.LOCAL,
        icon = "wooden_fish", keywords = resources.getStringArray(R.array.fish_keywords).toList(), sortOrder = 642, isFeatured = false)
    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = FishRoute(factory)
}
