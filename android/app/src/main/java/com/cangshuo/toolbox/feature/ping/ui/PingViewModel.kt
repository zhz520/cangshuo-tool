package com.cangshuo.toolbox.feature.ping.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.ping.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PingUiState(val host: String = "", val ipv6: Boolean = false, val count: Int = 4,
    val running: Boolean = false, val result: PingResult? = null, val error: PingError? = null, val cancelled: Boolean = false)
class PingViewModel(private val execute: ExecutePingUseCase,private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(PingUiState(saved["host"] ?: "",saved["ipv6"] ?: false,saved["count"] ?: 4))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    fun host(value: String) {
        if (mutable.value.running) return
        if (value.length > 256) { mutable.update { it.copy(error = PingError.INVALID_TARGET) };return }
        saved["host"] = value;mutable.update { it.copy(host = value,result = null,error = null,cancelled = false) }
    }
    fun ipv6(value: Boolean) { if (!mutable.value.running) { saved["ipv6"] = value;mutable.update { it.copy(ipv6 = value,result = null,error = null) } } }
    fun count(value: Int) { if (!mutable.value.running && value in setOf(1,4,8)) { saved["count"] = value;mutable.update { it.copy(count = value,result = null,error = null) } } }
    fun run() {
        if (mutable.value.running) return
        val input = mutable.value;val token = ++generation
        mutable.update { it.copy(running = true,result = null,error = null,cancelled = false) }
        job = viewModelScope.launch {
            try { val result = execute(input.host,input.ipv6,input.count);ensureActive();if (token == generation) mutable.update { it.copy(result = result) } }
            catch (e: CancellationException) { throw e }
            catch (e: PingFailure) { if (token == generation) mutable.update { it.copy(error = e.reason) } }
            catch (_: Exception) { if (token == generation) mutable.update { it.copy(error = PingError.FAILED) } }
            finally { if (token == generation) mutable.update { it.copy(running = false) } }
        }
    }
    fun cancel() { if (!mutable.value.running) return;generation++;job?.cancel();job = null;mutable.update { it.copy(running = false,cancelled = true) } }
    companion object { fun factory(execute: ExecutePingUseCase) = viewModelFactory { initializer { PingViewModel(execute,createSavedStateHandle()) } } }
}
