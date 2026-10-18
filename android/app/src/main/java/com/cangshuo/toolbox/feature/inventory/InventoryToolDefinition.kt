package com.cangshuo.toolbox.feature.inventory

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.inventory.ui.InventoryRoute

class InventoryToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {
    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "inventory_assets",
            name = resources.getString(R.string.inventory_name),
            description = resources.getString(R.string.inventory_description),
            category = ToolCategory.LIFE,
            mode = ToolMode.LOCAL,
            icon = "inventory_assets",
            keywords = resources.getStringArray(R.array.inventory_keywords).toList(),
            sortOrder = 633,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = InventoryRoute(factory)
}
