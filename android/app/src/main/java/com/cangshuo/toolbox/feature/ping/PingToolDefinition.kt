package com.cangshuo.toolbox.feature.ping

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.ping.ui.PingRoute

class PingToolDefinition(private val resources: Resources,private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata("ping",resources.getString(R.string.ping_name),resources.getString(R.string.ping_description),
        ToolCategory.NETWORK,ToolMode.LOCAL,icon="ping",keywords=resources.getStringArray(R.array.ping_keywords).toList(),sortOrder=410)
    override val requiredPermissions = emptyList<String>()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = PingRoute(factory)
}
