package com.cangshuo.toolbox.feature.hash

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.hash.ui.HashRoute

class HashToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "hash",
            name = resources.getString(R.string.hash_name),
            description = resources.getString(R.string.hash_description),
            category = ToolCategory.DEV,
            mode = ToolMode.LOCAL,
            icon = "hash",
            keywords = resources.getStringArray(R.array.hash_keywords).toList(),
            sortOrder = 150,
            isFeatured = true,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = HashRoute(factory)
}
