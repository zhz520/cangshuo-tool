package com.cangshuo.toolbox.feature.webtools

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.core.ui.ToolboxWebScreen

/**
 * WEB tool registered from the official site (Decision 019).
 *
 * Metadata mirrors the server catalog entry `qr_studio`; execution lives in `deploy/site/tools/qr_studio`
 * and is opened by the shared WebView container. The tool needs no Android permission, but requires a
 * network connection, which the container reports as an error state when loading fails.
 */
class QrStudioToolDefinition(private val resources: Resources) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "qr_studio",
            name = resources.getString(R.string.qr_studio_name),
            description = resources.getString(R.string.qr_studio_description),
            category = ToolCategory.QR,
            mode = ToolMode.WEB,
            icon = "qr",
            keywords = resources.getStringArray(R.array.qr_studio_keywords).toList(),
            sortOrder = 211,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = ToolboxWebScreen(title = metadata.name, code = metadata.code, onClose = {})
}
