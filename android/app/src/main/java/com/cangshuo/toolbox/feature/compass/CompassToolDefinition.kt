package com.cangshuo.toolbox.feature.compass

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.compass.ui.CompassRoute

class CompassToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata("compass",resources.getString(R.string.compass_name),resources.getString(R.string.compass_description),
        ToolCategory.SENSOR,ToolMode.LOCAL,icon="compass",keywords=resources.getStringArray(R.array.compass_keywords).toList(),sortOrder=320)
    override val requiredPermissions = emptyList<String>()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = CompassRoute(factory)
}
