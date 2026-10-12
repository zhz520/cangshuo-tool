package com.cangshuo.toolbox.feature.charconvert

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.charconvert.ui.CharConvertRoute

class CharConvertToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "char_convert",
            name = resources.getString(R.string.char_convert_name),
            description = resources.getString(R.string.char_convert_description),
            category = ToolCategory.TEXT,
            mode = ToolMode.LOCAL,
            icon = "char_convert",
            keywords = resources.getStringArray(R.array.char_convert_keywords).toList(),
            sortOrder = 85,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = CharConvertRoute(factory)
}
