package com.cangshuo.toolbox.feature.uuid

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.uuid.ui.UuidRoute

class UuidToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "uuid",
            name = resources.getString(R.string.uuid_name),
            description = resources.getString(R.string.uuid_description),
            category = ToolCategory.DEV,
            mode = ToolMode.LOCAL,
            icon = "uuid",
            keywords = resources.getStringArray(R.array.uuid_keywords).toList(),
            sortOrder = 120,
            isFeatured = true,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = UuidRoute(factory)
}
