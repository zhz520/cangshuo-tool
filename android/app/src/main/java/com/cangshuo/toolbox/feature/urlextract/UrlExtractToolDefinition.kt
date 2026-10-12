package com.cangshuo.toolbox.feature.urlextract

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.urlextract.ui.UrlExtractRoute

class UrlExtractToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "url_extract",
            name = resources.getString(R.string.url_extract_name),
            description = resources.getString(R.string.url_extract_description),
            category = ToolCategory.TEXT,
            mode = ToolMode.LOCAL,
            icon = "url_extract",
            keywords = resources.getStringArray(R.array.url_extract_keywords).toList(),
            sortOrder = 70,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = UrlExtractRoute(factory)
}
