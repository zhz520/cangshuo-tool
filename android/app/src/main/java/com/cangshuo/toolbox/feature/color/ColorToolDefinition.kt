package com.cangshuo.toolbox.feature.color

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.color.ui.ColorRoute

class ColorToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "color_converter",
            name = resources.getString(R.string.color_name),
            description = resources.getString(R.string.color_description),
            category = ToolCategory.CALC,
            mode = ToolMode.LOCAL,
            icon = "color_converter",
            keywords = resources.getStringArray(R.array.color_keywords).toList(),
            sortOrder = 55,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = ColorRoute(factory)
}
