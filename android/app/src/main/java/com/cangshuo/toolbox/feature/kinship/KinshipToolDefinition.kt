package com.cangshuo.toolbox.feature.kinship

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.kinship.ui.KinshipRoute

class KinshipToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "kinship",
            name = resources.getString(R.string.kinship_name),
            description = resources.getString(R.string.kinship_description),
            category = ToolCategory.CALC,
            mode = ToolMode.LOCAL,
            icon = "kinship",
            keywords = resources.getStringArray(R.array.kinship_keywords).toList(),
            sortOrder = 75,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = KinshipRoute(factory)
}
