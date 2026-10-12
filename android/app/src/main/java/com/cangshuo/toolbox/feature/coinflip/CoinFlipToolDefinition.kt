package com.cangshuo.toolbox.feature.coinflip

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.coinflip.ui.CoinFlipRoute

class CoinFlipToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata("coin_flip", resources.getString(R.string.coin_name), resources.getString(R.string.coin_description),
        ToolCategory.OTHER, ToolMode.LOCAL, icon = "coin_flip", keywords = resources.getStringArray(R.array.coin_keywords).toList(), sortOrder = 600)
    override val requiredPermissions = emptyList<String>()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = CoinFlipRoute(factory)
}
