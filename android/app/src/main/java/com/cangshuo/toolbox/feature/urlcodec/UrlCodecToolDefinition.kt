package com.cangshuo.toolbox.feature.urlcodec

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.urlcodec.ui.UrlCodecRoute

class UrlCodecToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "url_codec",
            name = resources.getString(R.string.url_codec_name),
            description = resources.getString(R.string.url_codec_description),
            category = ToolCategory.DEV,
            mode = ToolMode.LOCAL,
            icon = "url_codec",
            keywords = resources.getStringArray(R.array.url_codec_keywords).toList(),
            sortOrder = 140,
            isFeatured = true,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = UrlCodecRoute(factory)
}
