package com.cangshuo.toolbox.feature.imagebase64

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.imagebase64.ui.ImageBase64Route

class ImageBase64ToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "image_base64",
            name = resources.getString(R.string.image_base64_name),
            description = resources.getString(R.string.image_base64_description),
            category = ToolCategory.IMAGE,
            mode = ToolMode.LOCAL,
            icon = "image_base64",
            keywords = resources.getStringArray(R.array.image_base64_keywords).toList(),
            sortOrder = 106,
            isFeatured = false,
        )
    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true
    @Composable
    override fun Screen() = ImageBase64Route(factory)
}
