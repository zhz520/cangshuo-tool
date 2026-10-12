package com.cangshuo.toolbox.feature.textcount

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.textcount.ui.TextCountRoute

class TextCountToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "text_counter",
            name = resources.getString(R.string.text_count_name),
            description = resources.getString(R.string.text_count_description),
            category = ToolCategory.TEXT,
            mode = ToolMode.LOCAL,
            icon = "text_counter",
            keywords = resources.getStringArray(R.array.text_count_keywords).toList(),
            sortOrder = 65,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = TextCountRoute(factory)
}
