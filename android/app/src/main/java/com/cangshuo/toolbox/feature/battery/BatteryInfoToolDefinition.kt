package com.cangshuo.toolbox.feature.battery

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.battery.ui.BatteryInfoRoute

class BatteryInfoToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata("battery_info", resources.getString(R.string.battery_name), resources.getString(R.string.battery_description),
        ToolCategory.DEVICE, ToolMode.LOCAL, icon = "battery_info", keywords = resources.getStringArray(R.array.battery_keywords).toList(), sortOrder = 230)
    override val requiredPermissions = emptyList<String>()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = BatteryInfoRoute(factory)
}
