package com.cangshuo.toolbox

import android.content.res.Resources
import com.cangshuo.toolbox.core.tool.ToolRegistry
import com.cangshuo.toolbox.feature.calculator.CalculatorToolDefinition
import com.cangshuo.toolbox.feature.calculator.data.LocalCalculatorRepository
import com.cangshuo.toolbox.feature.calculator.domain.CalculateExpressionUseCase
import com.cangshuo.toolbox.feature.calculator.ui.CalculatorViewModel
import com.cangshuo.toolbox.feature.home.data.RegistryHomeRepository
import com.cangshuo.toolbox.feature.home.domain.GetHomeUseCase
import com.cangshuo.toolbox.feature.home.domain.OpenHomeToolUseCase
import com.cangshuo.toolbox.feature.home.ui.HomeViewModel
import com.cangshuo.toolbox.feature.search.data.RegistryToolSearchRepository
import com.cangshuo.toolbox.feature.search.domain.SearchToolsUseCase
import com.cangshuo.toolbox.feature.search.ui.SearchViewModel

/** Composition root. Add completed, trusted tool definitions to this collection. */
class ToolboxAppContainer(resources: Resources) {
    private val calculatorFactory = CalculatorViewModel.factory(
        CalculateExpressionUseCase(LocalCalculatorRepository()),
    )
    private val registry = ToolRegistry(definitions = listOf(CalculatorToolDefinition(resources, calculatorFactory)))
    private val repository = RegistryHomeRepository(registry)

    val searchViewModelFactory = SearchViewModel.factory(SearchToolsUseCase(RegistryToolSearchRepository(registry, resources)))

    val homeViewModelFactory = HomeViewModel.factory(
        getHome = GetHomeUseCase(repository),
        openTool = OpenHomeToolUseCase(repository),
    )
}
