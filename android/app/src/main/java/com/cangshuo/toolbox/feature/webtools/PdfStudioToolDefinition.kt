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

class PdfStudioToolDefinition(private val resources: Resources) : ToolDefinition {
    override val metadata get() = ToolMetadata(
        code = "pdf_studio", name = resources.getString(R.string.pdf_studio_name),
        description = resources.getString(R.string.pdf_studio_description), category = ToolCategory.PDF,
        mode = ToolMode.WEB, icon = "pdf", sortOrder = 310,
        keywords = listOf("PDF", "合并", "拆分", "提取", "旋转", "图片转PDF", "merge", "split", "rotate", "images"),
    )
    override val requiredPermissions = emptyList<String>()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = ToolboxWebScreen(metadata.name, metadata.code, {})
}
