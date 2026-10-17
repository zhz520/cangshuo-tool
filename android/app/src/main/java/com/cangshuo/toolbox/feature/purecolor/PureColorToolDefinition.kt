package com.cangshuo.toolbox.feature.purecolor

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.purecolor.ui.PureColorRoute

class PureColorToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "pure_color_image",
            name = resources.getString(R.string.pure_color_name),
            description = resources.getString(R.string.pure_color_description),
            category = ToolCategory.IMAGE,
            mode = ToolMode.LOCAL,
            icon = "pure_color_image",
            keywords = resources.getStringArray(R.array.pure_color_keywords).toList(),
            sortOrder = 107,
            isFeatured = false,
        )
    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true
    @Composable
    override fun Screen() = PureColorRoute(factory)
}
