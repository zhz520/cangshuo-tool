package com.cangshuo.toolbox.feature.workvalue

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.workvalue.ui.WorkValueRoute

class WorkValueToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "work_value",
            name = resources.getString(R.string.work_value_name),
            description = resources.getString(R.string.work_value_description),
            category = ToolCategory.CALC,
            mode = ToolMode.LOCAL,
            icon = "work_value",
            keywords = resources.getStringArray(R.array.work_value_keywords).toList(),
            sortOrder = 58,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = WorkValueRoute(factory)
}
