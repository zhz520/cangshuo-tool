package com.cangshuo.toolbox.feature.reaction

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.reaction.ui.ReactionRoute

class ReactionToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata(
        code = "reaction_test", name = resources.getString(R.string.reaction_name),
        description = resources.getString(R.string.reaction_description), category = ToolCategory.OTHER,
        mode = ToolMode.LOCAL, icon = "reaction_test", keywords = resources.getStringArray(R.array.reaction_keywords).toList(),
        sortOrder = 638, isFeatured = false,
    )
    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = ReactionRoute(factory)
}
