package com.cangshuo.toolbox.feature.chineseconvert

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.chineseconvert.ui.ChineseConversionRoute

class ChineseConversionToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "chinese_convert",
            name = resources.getString(R.string.chinese_convert_name),
            description = resources.getString(R.string.chinese_convert_description),
            category = ToolCategory.TEXT,
            mode = ToolMode.LOCAL,
            icon = "chinese_convert",
            keywords = resources.getStringArray(R.array.chinese_convert_keywords).toList(),
            sortOrder = 100,
            isFeatured = false,
        )
    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true
    @Composable
    override fun Screen() = ChineseConversionRoute(factory)
}
