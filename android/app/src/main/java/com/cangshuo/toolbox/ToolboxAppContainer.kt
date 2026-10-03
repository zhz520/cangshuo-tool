package com.cangshuo.toolbox

import com.cangshuo.toolbox.core.tool.ToolRegistry
import com.cangshuo.toolbox.feature.home.data.RegistryHomeRepository
import com.cangshuo.toolbox.feature.home.domain.GetHomeUseCase
import com.cangshuo.toolbox.feature.home.domain.OpenHomeToolUseCase
import com.cangshuo.toolbox.feature.home.ui.HomeViewModel

/** Composition root. Add completed, trusted tool definitions to this collection. */
class ToolboxAppContainer {
    private val registry = ToolRegistry(definitions = emptyList())
    private val repository = RegistryHomeRepository(registry)

    val homeViewModelFactory = HomeViewModel.factory(
        getHome = GetHomeUseCase(repository),
        openTool = OpenHomeToolUseCase(repository),
    )
}
