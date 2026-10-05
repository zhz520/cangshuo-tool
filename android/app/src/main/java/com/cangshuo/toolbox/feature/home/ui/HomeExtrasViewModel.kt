package com.cangshuo.toolbox.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.core.tool.ToolRegistryStore
import com.cangshuo.toolbox.feature.home.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeExtrasState(
    val recommendations: List<HomeRecommendation> = emptyList(), val announcements: List<HomeAnnouncement> = emptyList(),
    val ready: Boolean = false, val busy: Boolean = false, val failed: Boolean = false,
)
class HomeExtrasViewModel(private val useCases: HomeExtrasUseCases, private val registry: ToolRegistryStore,
    private val now: () -> Long = System::currentTimeMillis) : ViewModel() {
    private val mutable = MutableStateFlow(HomeExtrasState())
    val state = mutable.asStateFlow()
    private var recommendations = emptyList<HomeRecommendation>()
    private var lastAttempt: Long? = null
    init { viewModelScope.launch { registry.snapshots.collect { publishRecommendations() } } }
    private fun publishRecommendations() {
        val enabled = registry.current.enabledTools().map { it.code }.toSet()
        mutable.update { it.copy(recommendations = recommendations.filter { item -> item.toolCode == null || item.toolCode in enabled }) }
    }
    fun refresh(manual: Boolean = false) {
        if (mutable.value.busy) return
        val time = now()
        if (!manual && lastAttempt?.let { time >= it && time - it < 300_000 } == true) return
        lastAttempt = time
        mutable.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            var failed = false
            try {
                try { recommendations = useCases.recommendations(); publishRecommendations() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                try { val values = useCases.announcements(); mutable.update { it.copy(announcements = values) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
            } finally { mutable.update { it.copy(ready = true, busy = false, failed = failed) } }
        }
    }
    companion object {
        fun factory(useCases: HomeExtrasUseCases, registry: ToolRegistryStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { HomeExtrasViewModel(useCases, registry) } }
    }
}
