package com.cangshuo.toolbox.feature.radix

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.radix.ui.RadixRoute

class RadixToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "radix",
            name = resources.getString(R.string.radix_name),
            description = resources.getString(R.string.radix_description),
            category = ToolCategory.CALC,
            mode = ToolMode.LOCAL,
            icon = "radix",
            keywords = resources.getStringArray(R.array.radix_keywords).toList(),
            sortOrder = 25,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = RadixRoute(factory)
}
