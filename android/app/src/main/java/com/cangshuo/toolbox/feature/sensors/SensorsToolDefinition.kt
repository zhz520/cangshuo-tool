package com.cangshuo.toolbox.feature.sensors

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.sensors.ui.SensorsRoute

class SensorsToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata("sensor_info", resources.getString(R.string.sensors_name), resources.getString(R.string.sensors_description),
        ToolCategory.SENSOR, ToolMode.LOCAL, icon = "sensor_info", keywords = resources.getStringArray(R.array.sensors_keywords).toList(), sortOrder = 310)
    override val requiredPermissions = emptyList<String>()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = SensorsRoute(factory)
}
