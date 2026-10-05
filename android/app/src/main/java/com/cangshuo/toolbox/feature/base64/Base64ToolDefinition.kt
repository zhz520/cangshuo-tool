package com.cangshuo.toolbox.feature.base64

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.base64.ui.Base64Route

class Base64ToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "base64",
            name = resources.getString(R.string.base64_name),
            description = resources.getString(R.string.base64_description),
            category = ToolCategory.DEV,
            mode = ToolMode.LOCAL,
            icon = "base64",
            keywords = resources.getStringArray(R.array.base64_keywords).toList(),
            sortOrder = 130,
            isFeatured = true,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = Base64Route(factory)
}
