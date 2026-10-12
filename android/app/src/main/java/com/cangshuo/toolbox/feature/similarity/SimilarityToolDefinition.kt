package com.cangshuo.toolbox.feature.similarity

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.similarity.ui.SimilarityRoute

class SimilarityToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "text_similarity",
            name = resources.getString(R.string.similarity_name),
            description = resources.getString(R.string.similarity_description),
            category = ToolCategory.TEXT,
            mode = ToolMode.LOCAL,
            icon = "text_similarity",
            keywords = resources.getStringArray(R.array.similarity_keywords).toList(),
            sortOrder = 90,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = SimilarityRoute(factory)
}
