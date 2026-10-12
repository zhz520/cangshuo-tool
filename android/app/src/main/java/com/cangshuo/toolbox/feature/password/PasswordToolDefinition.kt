package com.cangshuo.toolbox.feature.password

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.password.ui.PasswordRoute

class PasswordToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "password_gen",
            name = resources.getString(R.string.password_name),
            description = resources.getString(R.string.password_description),
            category = ToolCategory.TEXT,
            mode = ToolMode.LOCAL,
            icon = "password_gen",
            keywords = resources.getStringArray(R.array.password_keywords).toList(),
            sortOrder = 60,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = PasswordRoute(factory)
}
