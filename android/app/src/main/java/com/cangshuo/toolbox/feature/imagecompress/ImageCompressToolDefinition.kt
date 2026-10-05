package com.cangshuo.toolbox.feature.imagecompress

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.imagecompress.ui.ImageCompressRoute

class ImageCompressToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "image_compress",
            name = resources.getString(R.string.image_compress_name),
            description = resources.getString(R.string.image_compress_description),
            category = ToolCategory.IMAGE,
            mode = ToolMode.LOCAL,
            icon = "image_compress",
            keywords = resources.getStringArray(R.array.image_compress_keywords).toList(),
            sortOrder = 220,
            isFeatured = true,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = ImageCompressRoute(factory)
}
