package com.cangshuo.toolbox.feature.converter

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.converter.ui.ConverterRoute

class UnitConverterToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    // Identity and ordering stay fixed; display data follows framework resource configuration.
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "unit_converter",
            name = resources.getString(R.string.converter_name),
            description = resources.getString(R.string.converter_description),
            category = ToolCategory.CONVERT,
            mode = ToolMode.LOCAL,
            icon = "converter",
            keywords = resources.getStringArray(R.array.converter_keywords).toList(),
            sortOrder = 100,
            isFeatured = true,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = ConverterRoute(factory)
}
