package com.cangshuo.toolbox.feature.aitext

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.aitext.ui.AiTextRoute

class AiTextToolDefinition(private val resources: Resources, private val factory: ()->ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get()=ToolMetadata("ai_text",resources.getString(R.string.ai_name),resources.getString(R.string.ai_description),
        ToolCategory.AI,ToolMode.SERVER,icon="text",keywords=listOf("AI","摘要","改写","翻译","summarize","rewrite","translate"),requiresLogin=true,sortOrder=510)
    override val requiredPermissions=emptyList<String>()
    override fun isAvailable(context: Context)=true
    @Composable override fun Screen()=AiTextRoute(factory())
}
