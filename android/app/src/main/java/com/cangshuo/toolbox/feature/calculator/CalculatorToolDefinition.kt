package com.cangshuo.toolbox.feature.calculator

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.calculator.ui.CalculatorRoute

class CalculatorToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    // Identity and ordering stay fixed; display data follows framework resource configuration.
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "calculator",
            name = resources.getString(R.string.calculator_name),
            description = resources.getString(R.string.calculator_description),
            category = ToolCategory.CALC,
            mode = ToolMode.LOCAL,
            icon = "calculator",
            keywords = resources.getStringArray(R.array.calculator_keywords).toList(),
            sortOrder = 10,
            isFeatured = true,
        )
    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = CalculatorRoute(factory)
}
