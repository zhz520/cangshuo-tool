package com.cangshuo.toolbox.feature.drawboard

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.drawboard.ui.DrawingBoardRoute

class DrawingBoardToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "drawing_board",
            name = resources.getString(R.string.drawing_board_name),
            description = resources.getString(R.string.drawing_board_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "drawing_board",
            keywords = resources.getStringArray(R.array.drawing_board_keywords).toList(),
            sortOrder = 628,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = DrawingBoardRoute(factory)
}
