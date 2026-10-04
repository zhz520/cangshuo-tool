package com.cangshuo.toolbox

import android.content.Context
import androidx.room.Room
import com.cangshuo.toolbox.core.database.ToolboxDatabase
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
import com.cangshuo.toolbox.feature.favorites.data.RoomFavoriteRepository
import com.cangshuo.toolbox.feature.favorites.domain.ObserveFavoritesUseCase
import com.cangshuo.toolbox.feature.favorites.domain.SetFavoriteUseCase
import com.cangshuo.toolbox.feature.favorites.ui.FavoritesViewModel

/** Composition root. Add completed, trusted tool definitions to this collection. */
class ToolboxAppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val resources = applicationContext.resources
    private val calculatorFactory = CalculatorViewModel.factory(
        CalculateExpressionUseCase(LocalCalculatorRepository()),
    )
    private val registry = ToolRegistry(definitions = listOf(CalculatorToolDefinition(resources, calculatorFactory)))
    private val repository = RegistryHomeRepository(registry)
    private val database = Room.databaseBuilder(applicationContext, ToolboxDatabase::class.java, "toolbox.db").build()
    private val favorites = RoomFavoriteRepository(database.favoriteToolDao(), registry)

    val favoritesViewModelFactory = FavoritesViewModel.factory(ObserveFavoritesUseCase(favorites), SetFavoriteUseCase(favorites))

    val searchViewModelFactory = SearchViewModel.factory(SearchToolsUseCase(RegistryToolSearchRepository(registry, resources)))

    val homeViewModelFactory = HomeViewModel.factory(
        getHome = GetHomeUseCase(repository),
        openTool = OpenHomeToolUseCase(repository),
    )
}
