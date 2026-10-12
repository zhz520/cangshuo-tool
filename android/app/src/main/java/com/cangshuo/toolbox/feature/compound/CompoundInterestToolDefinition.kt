package com.cangshuo.toolbox.feature.compound

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.compound.ui.CompoundRoute

class CompoundInterestToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "compound_interest",
            name = resources.getString(R.string.compound_name),
            description = resources.getString(R.string.compound_description),
            category = ToolCategory.CALC,
            mode = ToolMode.LOCAL,
            icon = "compound_interest",
            keywords = resources.getStringArray(R.array.compound_keywords).toList(),
            sortOrder = 35,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = CompoundRoute(factory)
}
