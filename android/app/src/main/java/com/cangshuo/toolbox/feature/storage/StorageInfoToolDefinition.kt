package com.cangshuo.toolbox.feature.storage

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.storage.ui.StorageInfoRoute

class StorageInfoToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata("storage_info", resources.getString(R.string.storage_name),
        resources.getString(R.string.storage_description), ToolCategory.DEVICE, ToolMode.LOCAL, icon = "storage_info",
        keywords = resources.getStringArray(R.array.storage_keywords).toList(), sortOrder = 220)
    override val requiredPermissions = emptyList<String>()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = StorageInfoRoute(factory)
}
