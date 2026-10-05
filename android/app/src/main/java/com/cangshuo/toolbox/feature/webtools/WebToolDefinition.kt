package com.cangshuo.toolbox.feature.webtools

import android.content.Context
import androidx.compose.runtime.Composable
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.core.ui.ToolboxWebScreen

/**
 * WEB tool built from catalogue metadata (Decision 019).
 *
 * The server owns name, description, category, keywords and ordering; the app only needs the code so
 * the shared WebView container can resolve `/tools/<code>/`. The open path is owned by the home
 * route, which branches on [ToolMetadata.mode] before calling [Screen].
 */
class WebToolDefinition(remoteMetadata: ToolMetadata) : ToolDefinition {
    override val metadata: ToolMetadata = remoteMetadata

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = ToolboxWebScreen(title = metadata.name, code = metadata.code, onClose = {})
}
