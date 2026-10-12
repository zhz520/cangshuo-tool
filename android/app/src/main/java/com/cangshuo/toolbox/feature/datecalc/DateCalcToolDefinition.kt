package com.cangshuo.toolbox.feature.datecalc

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.datecalc.ui.DateCalcRoute

class DateCalcToolDefinition(
    private val resources: Resources,
    private val factory: ViewModelProvider.Factory,
) : ToolDefinition {

    override val metadata: ToolMetadata
        get() = ToolMetadata(
            code = "date_calc",
            name = resources.getString(R.string.date_calc_name),
            description = resources.getString(R.string.date_calc_description),
            category = ToolCategory.CALC,
            mode = ToolMode.LOCAL,
            icon = "date_calc",
            keywords = resources.getStringArray(R.array.date_calc_keywords).toList(),
            sortOrder = 50,
            isFeatured = false,
        )

    override val requiredPermissions: List<String> = emptyList()

    override fun isAvailable(context: Context): Boolean = true

    @Composable
    override fun Screen() = DateCalcRoute(factory)
}
