package com.cangshuo.toolbox.feature.diagnostics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.BuildConfig
import com.cangshuo.toolbox.feature.diagnostics.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagnosticsState(val loading: Boolean = false, val failed: Boolean = false, val data: Diagnostics? = null)
class DiagnosticsViewModel(private val cases: DiagnosticsUseCases) : ViewModel() {
    private val mutable = MutableStateFlow(DiagnosticsState())
    val state = mutable.asStateFlow()
    fun refresh() = perform { cases.read() }
    fun clear() = perform { cases.clear(); cases.read() }
    private fun perform(action: suspend () -> Diagnostics) {
        if (mutable.value.loading) return
        mutable.value = mutable.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            try { mutable.value = DiagnosticsState(data = action()) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, failed = true) }
        }
    }
    fun report(sdk: Int): String? = mutable.value.data?.let { data ->
        "app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nsdk=$sdk\nsystemHistory=${data.systemHistoryAvailable}\n" +
            data.events.joinToString("\n") { "${java.time.Instant.ofEpochMilli(it.timestamp)} ${it.reason.name}" }
    }
    companion object { fun factory(cases: DiagnosticsUseCases) = viewModelFactory { initializer { DiagnosticsViewModel(cases) } } }
}
