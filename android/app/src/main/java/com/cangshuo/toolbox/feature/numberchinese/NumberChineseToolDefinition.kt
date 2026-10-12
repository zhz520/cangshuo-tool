package com.cangshuo.toolbox.feature.numberchinese

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.numberchinese.ui.NumberChineseRoute

class NumberChineseToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "number_chinese",
            name = resources.getString(R.string.number_chinese_name),
            description = resources.getString(R.string.number_chinese_description),
            category = ToolCategory.TEXT,
            mode = ToolMode.LOCAL,
            icon = "number_chinese",
            keywords = resources.getStringArray(R.array.number_chinese_keywords).toList(),
            sortOrder = 80,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = NumberChineseRoute(factory)
}
