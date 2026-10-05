package com.cangshuo.toolbox.core.tool

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.core.database.FavoriteToolDao
import com.cangshuo.toolbox.core.database.FavoriteToolEntity
import com.cangshuo.toolbox.core.database.RecentToolDao
import com.cangshuo.toolbox.core.database.RecentToolEntity
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.feature.favorites.data.RoomFavoriteRepository
import com.cangshuo.toolbox.feature.favorites.domain.FavoriteItem
import com.cangshuo.toolbox.feature.favorites.domain.ObserveFavoritesUseCase
import com.cangshuo.toolbox.feature.home.data.RegistryHomeRepository
import com.cangshuo.toolbox.feature.home.domain.GetHomeUseCase
import com.cangshuo.toolbox.feature.home.domain.HomeContent
import com.cangshuo.toolbox.feature.home.domain.OpenHomeToolUseCase
import com.cangshuo.toolbox.feature.home.ui.HomeCatalogState
import com.cangshuo.toolbox.feature.home.ui.HomeTab
import com.cangshuo.toolbox.feature.home.ui.HomeViewModel
import com.cangshuo.toolbox.feature.recent.data.RoomRecentRepository
import com.cangshuo.toolbox.feature.recent.domain.ObserveRecentUseCase
import com.cangshuo.toolbox.feature.recent.domain.RecentItem
import com.cangshuo.toolbox.feature.search.data.RegistryToolSearchRepository
import com.cangshuo.toolbox.feature.search.domain.SearchToolsUseCase
import com.cangshuo.toolbox.feature.search.ui.SearchResultsState
import com.cangshuo.toolbox.feature.search.ui.SearchViewModel
import com.cangshuo.toolbox.feature.webtools.domain.CatalogSyncState
import com.cangshuo.toolbox.feature.webtools.domain.RequestWebToolCatalogSyncUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Regressions for a remote catalog arriving while screens and Room observers are already active. */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolCatalogUpdatesTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private var modelCount = 0
    private val bundled = tool("calculator", mode = ToolMode.LOCAL, category = ToolCategory.CALC)
    private val store = ToolRegistryStore(ToolRegistry(listOf(bundled)))

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun bundledCatalogIsImmediatelyReadable() {
        assertSame(bundled, store.current.find("calculator"))
        assertEquals(listOf("calculator"), RegistryHomeRepository(store).getTools().map { it.code })
    }
    @Test fun localizedBundledReplacementRetainsRemoteToolsAndHomeSelection() = runTest {
        store.replaceRemoteWebTools(listOf(tool("remote_qr")))
        val model=home();runCurrent();model.selectTab(HomeTab.TOOLS)
        store.replaceBundled(ToolRegistry(listOf(tool("calculator",name="Calculator",mode=ToolMode.LOCAL,category=ToolCategory.CALC))))
        runCurrent()
        assertEquals("Calculator",store.current.find("calculator")!!.metadata.name)
        assertNotNull(store.current.find("remote_qr"));assertEquals(HomeTab.TOOLS,model.uiState.value.tab)
        store.replaceRemoteWebTools(emptyList())
        assertNull(store.current.find("remote_qr"));assertEquals("Calculator",store.current.find("calculator")!!.metadata.name)
    }
    @Test fun startupWaitsForRestoredSettingsAndDoesNotOverrideNavigation() = runTest {
        val settings=MutableStateFlow(com.cangshuo.toolbox.feature.sync.domain.AppSettings())
        val repository=RegistryHomeRepository(store)
        val model=keep(HomeViewModel(GetHomeUseCase(repository),OpenHomeToolUseCase(repository),SavedStateHandle(),settings=settings))
        runCurrent();assertEquals(HomeTab.HOME,model.uiState.value.tab)
        settings.value=com.cangshuo.toolbox.feature.sync.domain.AppSettings(startupPage="FAVORITES",ready=true)
        runCurrent();assertEquals(HomeTab.FAVORITES,model.uiState.value.tab)
        model.selectTab(HomeTab.TOOLS)
        settings.value=settings.value.copy(startupPage="PROFILE")
        runCurrent();assertEquals(HomeTab.TOOLS,model.uiState.value.tab)
    }

    @Test
    fun remoteArrivalUpdatesActiveHomeAndCountsWithoutRefresh() = runTest {
        val model = home()
        runCurrent()
        model.selectCategory(ToolCategory.QR)
        model.openSearch()
        store.replaceRemoteWebTools(listOf(tool("web_qr", featured = true)))
        runCurrent()

        val state = model.uiState.value
        assertEquals(HomeTab.TOOLS, state.tab)
        assertEquals(ToolCategory.QR, state.category)
        assertTrue(state.isSearchOpen)
        val content = content(model)
        assertEquals(listOf("web_qr"), content.tools.map { it.code })
        assertEquals(content.tools, content.commonTools)
        assertEquals(content.tools, content.featuredTools)
        assertEquals(2, content.totalToolCount)
        assertEquals(1, content.categories.single { it.category == ToolCategory.QR }.toolCount)
    }

    @Test
    fun arrivalBeforeObserverStartsIsNotLost() = runTest {
        val model = home()
        // The observer has been launched but its first collection has not run yet.
        store.replaceRemoteWebTools(listOf(tool("web_qr")))
        runCurrent()
        assertEquals(listOf("calculator", "web_qr"), content(model).tools.map { it.code })
    }

    @Test
    fun activeSearchRecomputesAndRetainsFiltersAndRecentPriority() = runTest {
        val model = search()
        model.updateQuery("studio")
        model.selectCategory(ToolCategory.QR)
        model.updateFavorites(setOf("alpha"))
        model.updateRecent(setOf("beta"))
        runCurrent()
        assertEquals(SearchResultsState.Empty, model.uiState.value.results)

        store.replaceRemoteWebTools(listOf(tool("alpha", "studio"), tool("beta", "studio")))
        runCurrent()
        val state = model.uiState.value
        assertEquals("studio", state.query)
        assertEquals(ToolCategory.QR, state.category)
        assertEquals(listOf("beta", "alpha"), (state.results as SearchResultsState.Content).tools.map { it.code })
    }

    @Test
    fun refreshedMetadataAndRemovalReachBothActiveScreens() = runTest {
        val home = home()
        val search = search()
        search.updateQuery("studio")
        store.replaceRemoteWebTools(listOf(tool("web_qr", "studio old")))
        runCurrent()
        store.replaceRemoteWebTools(listOf(tool("web_qr", "studio new", featured = true)))
        runCurrent()
        assertEquals("studio new", content(home).tools.single { it.code == "web_qr" }.name)
        assertEquals("studio new", (search.uiState.value.results as SearchResultsState.Content).tools.single().name)

        store.replaceRemoteWebTools(emptyList())
        runCurrent()
        assertEquals(listOf("calculator"), content(home).tools.map { it.code })
        assertEquals(SearchResultsState.Empty, search.uiState.value.results)
    }

    @Test
    fun backgroundUpdateDoesNotReplaceContentWithLoading() = runTest {
        val model = home()
        val states = mutableListOf<HomeCatalogState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.uiState.collect { states += it.catalog }
        }
        runCurrent()
        states.clear()
        store.replaceRemoteWebTools(listOf(tool("web_qr")))
        runCurrent()
        assertTrue(states.isNotEmpty())
        assertTrue(states.all { it is HomeCatalogState.Content })
    }

    @Test
    fun bundledNativeAndWebDefinitionsWinCodeCollisions() {
        val bundledWeb = tool("qr_studio", "bundled")
        val registry = ToolRegistryStore(ToolRegistry(listOf(bundled, bundledWeb)))
        registry.replaceRemoteWebTools(listOf(tool("calculator", "remote"), tool("qr_studio", "remote")))
        assertSame(bundled, registry.current.find("calculator"))
        assertSame(bundledWeb, registry.current.find("qr_studio"))
        assertEquals(2, registry.current.tools.size)
    }

    @Test
    fun rejectsRemoteNativeDefinitionsWithoutChangingSnapshot() {
        store.replaceRemoteWebTools(listOf(tool("web_qr")))
        val before = store.current
        assertThrows(IllegalArgumentException::class.java) {
            store.replaceRemoteWebTools(listOf(tool("native", mode = ToolMode.LOCAL)))
        }
        assertSame(before, store.current)
    }

    @Test
    fun replacementDropsOldRemoteEntriesDeduplicatesAndSorts() {
        store.replaceRemoteWebTools(listOf(tool("old")))
        val first = tool("alpha", "first")
        store.replaceRemoteWebTools(listOf(tool("beta"), first, tool("alpha", "duplicate")))
        assertNull(store.current.find("old"))
        assertSame(first, store.current.find("alpha"))
        assertEquals(listOf("alpha", "beta", "calculator"), store.current.tools.map { it.code })
    }

    @Test
    fun catalogChangesResolveExistingFavoritesWithoutDaoWrites() = runTest {
        val dao = FavoriteDao(listOf(FavoriteToolEntity("web_qr", 123)))
        val values = mutableListOf<List<FavoriteItem>>()
        val observe = ObserveFavoritesUseCase(RoomFavoriteRepository(dao, store))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe().collect { values += it } }
        runCurrent()
        assertNull(values.last().single().metadata)
        store.replaceRemoteWebTools(listOf(tool("web_qr", "first")))
        runCurrent()
        assertEquals("first", values.last().single().metadata?.name)
        store.replaceRemoteWebTools(listOf(tool("web_qr", "second")))
        runCurrent()
        assertEquals("second", values.last().single().metadata?.name)
        store.replaceRemoteWebTools(emptyList())
        runCurrent()
        assertNull(values.last().single().metadata)
        assertEquals(123L, values.last().single().addedAt)
        assertEquals(0, dao.writes)
    }

    @Test
    fun catalogChangesResolveRecentRecordsWithoutChangingUsage() = runTest {
        val dao = RecentDao(listOf(RecentToolEntity("web_qr", 456, 7)))
        val values = mutableListOf<List<RecentItem>>()
        val observe = ObserveRecentUseCase(RoomRecentRepository(dao, store))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe().collect { values += it } }
        runCurrent()
        assertNull(values.last().single().metadata)
        store.replaceRemoteWebTools(listOf(tool("web_qr", "first")))
        runCurrent()
        assertEquals("first", values.last().single().metadata?.name)
        store.replaceRemoteWebTools(listOf(tool("web_qr", "second")))
        runCurrent()
        assertEquals("second", values.last().single().metadata?.name)
        store.replaceRemoteWebTools(emptyList())
        runCurrent()
        val item = values.last().single()
        assertNull(item.metadata)
        assertEquals(456L, item.lastUsedAt)
        assertEquals(7, item.useCount)
        assertEquals(0, dao.writes)
    }

    @Test
    fun clearedViewModelsStopObservingCatalog() = runTest {
        val home = home()
        val search = search()
        runCurrent()
        val homeBefore = home.uiState.value
        val searchBefore = search.uiState.value
        viewModels.clear()
        store.replaceRemoteWebTools(listOf(tool("web_qr")))
        runCurrent()
        assertEquals(homeBefore, home.uiState.value)
        assertEquals(searchBefore, search.uiState.value)
    }

    @Test
    fun remoteRefreshProgressKeepsContentAndCurrentFilters() = runTest {
        store.replaceRemoteWebTools(listOf(tool("web_old")))
        val response = CompletableDeferred<Boolean>()
        val sync = RequestWebToolCatalogSyncUseCase(backgroundScope, { false }, {
            response.await()
            store.replaceRemoteWebTools(listOf(tool("web_new")))
            true
        }) { testScheduler.currentTime }
        val model = home(sync)
        model.selectCategory(ToolCategory.QR)
        model.openSearch()
        model.refreshRemoteCatalog()
        runCurrent()
        assertEquals(CatalogSyncState.REFRESHING, model.uiState.value.sync)
        assertEquals(listOf("web_old"), content(model).tools.map { it.code })
        assertEquals(ToolCategory.QR, model.uiState.value.category)
        assertTrue(model.uiState.value.isSearchOpen)
        response.complete(true)
        runCurrent()
        assertEquals(CatalogSyncState.UPDATED, model.uiState.value.sync)
        assertEquals(listOf("web_new"), content(model).tools.map { it.code })
        assertEquals(ToolCategory.QR, model.uiState.value.category)
        assertTrue(model.uiState.value.isSearchOpen)
    }

    @Test
    fun clearingScreenDoesNotCancelSharedRefreshAndRecreatedScreenGetsResult() = runTest {
        val response = CompletableDeferred<Boolean>()
        val sync = RequestWebToolCatalogSyncUseCase(backgroundScope, { false }, {
            response.await()
            store.replaceRemoteWebTools(listOf(tool("web_new")))
            true
        }) { testScheduler.currentTime }
        val model = home(sync)
        model.refreshRemoteCatalog()
        runCurrent()
        viewModels.clear()
        response.complete(true)
        runCurrent()
        assertNotNull(store.current.find("web_new"))
        val recreated = home(sync)
        runCurrent()
        assertEquals(CatalogSyncState.UPDATED, recreated.uiState.value.sync)
        assertTrue(content(recreated).tools.any { it.code == "web_new" })
    }

    private fun home(sync: RequestWebToolCatalogSyncUseCase? = null): HomeViewModel {
        val repository = RegistryHomeRepository(store)
        return keep(HomeViewModel(GetHomeUseCase(repository), OpenHomeToolUseCase(repository), SavedStateHandle(), sync))
    }

    private fun search(): SearchViewModel = keep(
        SearchViewModel(
            SearchToolsUseCase(RegistryToolSearchRepository(store, ToolCategory.entries.associateWith { listOf(it.code) })),
            SavedStateHandle(),
        ),
    )

    private fun <T : ViewModel> keep(model: T): T {
        viewModels.put("model${modelCount++}", model)
        return model
    }

    private fun content(model: HomeViewModel): HomeContent = when (val catalog = model.uiState.value.catalog) {
        is HomeCatalogState.Content -> catalog.value
        is HomeCatalogState.Empty -> catalog.value
        else -> error("Expected resolved catalog")
    }

    private fun tool(
        code: String,
        name: String = code,
        mode: ToolMode = ToolMode.WEB,
        category: ToolCategory = ToolCategory.QR,
        featured: Boolean = false,
    ): ToolDefinition = object : ToolDefinition {
        override val metadata = ToolMetadata(code, name, "", category, mode, isFeatured = featured)
        override val requiredPermissions: List<String> = emptyList()
        override fun isAvailable(context: Context) = true
        @Composable override fun Screen() = Unit
    }

    private class FavoriteDao(initial: List<FavoriteToolEntity>) : FavoriteToolDao {
        private val entries = MutableStateFlow(initial)
        var writes = 0
        override suspend fun snapshot() = entries.value
        override fun observeFavorites() = entries
        override suspend fun add(favorite: FavoriteToolEntity) { writes++ }
        override suspend fun remove(toolCode: String) { writes++ }
    }

    private class RecentDao(initial: List<RecentToolEntity>) : RecentToolDao {
        private val entries = MutableStateFlow(initial)
        var writes = 0
        override suspend fun snapshot() = entries.value
        override fun observeRecent() = entries
        override suspend fun find(toolCode: String) = entries.value.find { it.toolCode == toolCode }
        override suspend fun upsert(recent: RecentToolEntity) { writes++ }
        override suspend fun remove(toolCode: String) { writes++ }
        override suspend fun trimTo(maxEntries: Int) { writes++ }
        override suspend fun clear() { writes++ }
    }
}
