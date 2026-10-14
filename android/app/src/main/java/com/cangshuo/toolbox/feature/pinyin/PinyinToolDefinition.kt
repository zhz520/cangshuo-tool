package com.cangshuo.toolbox.feature.pinyin

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.pinyin.ui.PinyinRoute

class PinyinToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "pinyin",
            name = resources.getString(R.string.pinyin_name),
            description = resources.getString(R.string.pinyin_description),
            category = ToolCategory.TEXT,
            mode = ToolMode.LOCAL,
            icon = "pinyin",
            keywords = resources.getStringArray(R.array.pinyin_keywords).toList(),
            sortOrder = 95,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = PinyinRoute(factory)
}
