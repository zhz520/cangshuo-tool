package com.cangshuo.toolbox.feature.level

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.level.ui.LevelRoute

class LevelToolDefinition(private val resources: Resources,private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata("level",resources.getString(R.string.level_name),resources.getString(R.string.level_description),
        ToolCategory.SENSOR,ToolMode.LOCAL,icon="level",keywords=resources.getStringArray(R.array.level_keywords).toList(),sortOrder=330)
    override val requiredPermissions = emptyList<String>()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = LevelRoute(factory)
}
