package com.cangshuo.toolbox.feature.deviceinfo

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.deviceinfo.ui.DeviceInfoRoute

class DeviceInfoToolDefinition(private val resources: Resources,private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get()=ToolMetadata("device_info",resources.getString(R.string.device_info_name),
        resources.getString(R.string.device_info_description),ToolCategory.DEVICE,ToolMode.LOCAL,icon="device_info",
        keywords=resources.getStringArray(R.array.device_info_keywords).toList(),sortOrder=210)
    override val requiredPermissions=emptyList<String>()
    override fun isAvailable(context: Context)=true
    @Composable override fun Screen()=DeviceInfoRoute(factory)
}
