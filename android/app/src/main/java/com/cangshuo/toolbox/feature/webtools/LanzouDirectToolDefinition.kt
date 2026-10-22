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

class LanzouDirectToolDefinition(private val resources: Resources) : ToolDefinition {
    override val metadata get() = ToolMetadata(
        code = "lanzou_direct", name = resources.getString(R.string.lanzou_direct_name),
        description = resources.getString(R.string.lanzou_direct_description), category = ToolCategory.NETWORK,
        mode = ToolMode.WEB, icon = "link", sortOrder = 631,
        keywords = listOf("蓝奏云", "蓝奏", "直链", "下载", "分享密码", "lanzou", "direct link"),
    )
    override val requiredPermissions = emptyList<String>()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = ToolboxWebScreen(metadata.name, metadata.code, {})
}
