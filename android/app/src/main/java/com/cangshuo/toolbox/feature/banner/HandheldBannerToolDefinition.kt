package com.cangshuo.toolbox.feature.banner

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.banner.ui.BannerRoute

class HandheldBannerToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "handheld_banner",
            name = resources.getString(R.string.handheld_banner_name),
            description = resources.getString(R.string.handheld_banner_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "handheld_banner",
            keywords = resources.getStringArray(R.array.handheld_banner_keywords).toList(),
            sortOrder = 625,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = BannerRoute(factory)
}
