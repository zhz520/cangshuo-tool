package com.cangshuo.toolbox.feature.lightmeter

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.lightmeter.ui.LightMeterRoute

class LightMeterToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata("light_meter", resources.getString(R.string.light_name), resources.getString(R.string.light_description),
        ToolCategory.SENSOR, ToolMode.LOCAL, icon = "light_meter", keywords = resources.getStringArray(R.array.light_keywords).toList(), sortOrder = 340)
    override val requiredPermissions = emptyList<String>()
    // Unsupported hardware is explained inside the tool, with a real retry action.
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = LightMeterRoute(factory)
}
