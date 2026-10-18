package com.cangshuo.toolbox.feature.anniversary

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.anniversary.ui.AnniversaryRoute

class AnniversaryToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "anniversary_countdown",
            name = resources.getString(R.string.anniversary_name),
            description = resources.getString(R.string.anniversary_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "anniversary_countdown",
            keywords = resources.getStringArray(R.array.anniversary_keywords).toList(),
            sortOrder = 631,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = AnniversaryRoute(factory)
}
