package com.cangshuo.toolbox.feature.colorpicker

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.colorpicker.ui.ColorPickerRoute

class ColorPickerToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "color_picker",
            name = resources.getString(R.string.color_picker_name),
            description = resources.getString(R.string.color_picker_description),
            category = ToolCategory.IMAGE,
            mode = ToolMode.LOCAL,
            icon = "color_picker",
            keywords = resources.getStringArray(R.array.color_picker_keywords).toList(),
            sortOrder = 105,
            isFeatured = false,
        )
    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true
    @Composable
    override fun Screen() = ColorPickerRoute(factory)
}
