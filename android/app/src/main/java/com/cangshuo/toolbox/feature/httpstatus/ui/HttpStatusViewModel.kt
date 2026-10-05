package com.cangshuo.toolbox.feature.httpstatus.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.httpstatus.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HttpStatusUiState(val url: String = "", val method: HttpMethod = HttpMethod.HEAD,
    val followRedirects: Boolean = true, val running: Boolean = false, val result: HttpProbeResult? = null,
    val error: HttpError? = null, val cancelled: Boolean = false)

class HttpStatusViewModel(private val probe: ProbeHttpStatusUseCase, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(HttpStatusUiState(
        url = saved.get<String>("url") ?: "",
        method = runCatching { HttpMethod.valueOf(saved.get<String>("method") ?: HttpMethod.HEAD.name) }.getOrDefault(HttpMethod.HEAD),
        followRedirects = saved.get<Boolean>("follow") ?: true,
    ))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L

    fun url(value: String) {
        if (mutable.value.running || value.length > 4096) return
        saved["url"] = value
        mutable.update { it.copy(url = value, result = null, error = null, cancelled = false) }
    }

    fun method(value: HttpMethod) {
        if (mutable.value.running) return
        saved["method"] = value.name
        mutable.update { it.copy(method = value, result = null, error = null) }
    }

    fun followRedirects(value: Boolean) {
        if (mutable.value.running) return
        saved["follow"] = value
        mutable.update { it.copy(followRedirects = value, result = null, error = null) }
    }

    fun run() {
        if (mutable.value.running) return
        val input = mutable.value
        val token = ++generation
        mutable.update { it.copy(running = true, result = null, error = null, cancelled = false) }
        job = viewModelScope.launch {
            try {
                val result = probe(input.url, input.method, input.followRedirects)
                ensureActive()
                if (token == generation) mutable.update { it.copy(result = result) }
            } catch (e: CancellationException) { throw e }
            catch (e: HttpProbeFailure) { if (token == generation) mutable.update { it.copy(error = e.reason) } }
            catch (_: Exception) { if (token == generation) mutable.update { it.copy(error = HttpError.FAILED) } }
            finally { if (token == generation) mutable.update { it.copy(running = false) } }
        }
    }

    fun cancel() {
        if (!mutable.value.running) return
        generation++
        job?.cancel()
        job = null
        mutable.update { it.copy(running = false, cancelled = true) }
    }

    companion object {
        fun factory(probe: ProbeHttpStatusUseCase) = viewModelFactory {
            initializer { HttpStatusViewModel(probe, createSavedStateHandle()) }
        }
    }
}
