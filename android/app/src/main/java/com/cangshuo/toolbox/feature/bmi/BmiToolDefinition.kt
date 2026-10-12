package com.cangshuo.toolbox.feature.bmi

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.bmi.ui.BmiRoute

class BmiToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "bmi",
            name = resources.getString(R.string.bmi_name),
            description = resources.getString(R.string.bmi_description),
            category = ToolCategory.CALC,
            mode = ToolMode.LOCAL,
            icon = "bmi",
            keywords = resources.getStringArray(R.array.bmi_keywords).toList(),
            sortOrder = 30,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = BmiRoute(factory)
}
