package com.cangshuo.toolbox.feature.noise

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.noise.ui.NoiseRoute

class NoiseToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "white_noise",
            name = resources.getString(R.string.noise_name),
            description = resources.getString(R.string.noise_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "white_noise",
            keywords = resources.getStringArray(R.array.noise_keywords).toList(),
            sortOrder = 635,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = NoiseRoute(factory)
}
