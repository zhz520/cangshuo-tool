package com.cangshuo.toolbox.feature.mortgage

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.mortgage.ui.MortgageRoute

class MortgageToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "mortgage",
            name = resources.getString(R.string.mortgage_name),
            description = resources.getString(R.string.mortgage_description),
            category = ToolCategory.CALC,
            mode = ToolMode.LOCAL,
            icon = "mortgage",
            keywords = resources.getStringArray(R.array.mortgage_keywords).toList(),
            sortOrder = 40,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = MortgageRoute(factory)
}
