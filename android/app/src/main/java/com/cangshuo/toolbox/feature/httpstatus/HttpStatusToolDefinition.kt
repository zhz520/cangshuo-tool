package com.cangshuo.toolbox.feature.httpstatus

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.httpstatus.ui.HttpStatusRoute

class HttpStatusToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata("http_status", resources.getString(R.string.http_status_name),
        resources.getString(R.string.http_status_description), ToolCategory.NETWORK, ToolMode.LOCAL, icon = "http_status",
        keywords = resources.getStringArray(R.array.http_status_keywords).toList(), sortOrder = 420)
    override val requiredPermissions = emptyList<String>()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = HttpStatusRoute(factory)
}
