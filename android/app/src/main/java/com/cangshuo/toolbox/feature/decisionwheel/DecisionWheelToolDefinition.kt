package com.cangshuo.toolbox.feature.decisionwheel

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.decisionwheel.ui.DecisionWheelRoute

class DecisionWheelToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "decision_wheel",
            name = resources.getString(R.string.decision_wheel_name),
            description = resources.getString(R.string.decision_wheel_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "decision_wheel",
            keywords = resources.getStringArray(R.array.decision_wheel_keywords).toList(),
            sortOrder = 627,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = DecisionWheelRoute(factory)
}
