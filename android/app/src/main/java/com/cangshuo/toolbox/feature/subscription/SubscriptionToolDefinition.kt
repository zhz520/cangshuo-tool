package com.cangshuo.toolbox.feature.subscription

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.subscription.ui.SubscriptionRoute

class SubscriptionToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "subscription_manager",
            name = resources.getString(R.string.subscription_name),
            description = resources.getString(R.string.subscription_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "subscription_manager",
            keywords = resources.getStringArray(R.array.subscription_keywords).toList(),
            sortOrder = 634,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = SubscriptionRoute(factory)
}
