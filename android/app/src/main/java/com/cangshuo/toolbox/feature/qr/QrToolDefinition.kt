package com.cangshuo.toolbox.feature.qr

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.qr.ui.QrRoute

class QrToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "qr",
            name = resources.getString(R.string.qr_name),
            description = resources.getString(R.string.qr_description),
            category = ToolCategory.QR,
            mode = ToolMode.LOCAL,
            icon = "qr",
            keywords = resources.getStringArray(R.array.qr_keywords).toList(),
            sortOrder = 210,
            isFeatured = true,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = QrRoute(factory)
}
