package com.cangshuo.toolbox.feature.feedback.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.feedback.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class FeedbackState(val user: Long? = null, val type: String = "BUG", val content: String = "", val contact: String = "",
    val items: List<FeedbackEntry> = emptyList(), val ready: Boolean = false, val busy: Boolean = false,
    val failure: FeedbackFailure? = null, val sent: Boolean = false) {
    override fun toString() = "FeedbackState[redacted]"
}
class FeedbackViewModel(private val useCases: FeedbackUseCases) : ViewModel() {
    private val mutable = MutableStateFlow(FeedbackState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var generation = 0L
    init { viewModelScope.launch { useCases.users.collect { user ->
        generation++; work?.cancel(); mutable.value = FeedbackState(user = user)
        if (user != null) refresh()
    } } }
    fun type(value: String) { if (!state.value.busy) mutable.update { it.copy(type = value, failure = null, sent = false) } }
    fun content(value: String) { if (!state.value.busy && value.length <= 2000) mutable.update { it.copy(content = value, failure = null, sent = false) } }
    fun contact(value: String) { if (!state.value.busy && value.length <= 128) mutable.update { it.copy(contact = value, failure = null, sent = false) } }
    fun refresh() = operation { user ->
        val values = useCases.mine(user)
        currentCoroutineContext().ensureActive()
        mutable.update { it.copy(items = values, ready = true) }
    }
    fun submit() {
        val draft = state.value
        operation { user ->
            val entry = useCases.submit(user, draft.type, draft.content, draft.contact)
            currentCoroutineContext().ensureActive()
            mutable.update { it.copy(items = (listOf(entry) + it.items.filter { item -> item.id != entry.id }).take(50),
                ready = true, content = "", contact = "", sent = true) }
        }
    }
    private fun operation(block: suspend (Long) -> Unit) {
        val user = state.value.user ?: return
        if (state.value.busy) return
        val token = generation
        mutable.update { it.copy(busy = true, failure = null, sent = false) }
        work = viewModelScope.launch {
            try { block(user) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: FeedbackException) { if (token == generation) mutable.update { it.copy(failure = error.failure) } }
            catch (_: Exception) { if (token == generation) mutable.update { it.copy(failure = FeedbackFailure.SERVICE) } }
            finally { if (token == generation) mutable.update { it.copy(busy = false) } }
        }
    }
    companion object {
        fun factory(useCases: FeedbackUseCases): ViewModelProvider.Factory = viewModelFactory { initializer { FeedbackViewModel(useCases) } }
    }
}
