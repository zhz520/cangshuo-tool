package com.cangshuo.toolbox.feature.similarity.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.similarity.domain.CompareTextsUseCase
import com.cangshuo.toolbox.feature.similarity.domain.SimilarityError
import com.cangshuo.toolbox.feature.similarity.domain.SimilarityLimits
import com.cangshuo.toolbox.feature.similarity.domain.SimilarityOptions
import com.cangshuo.toolbox.feature.similarity.domain.SimilarityOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SimilarityViewModel(
    private val compareTexts: CompareTextsUseCase,
    private val savedStateHandle: SavedStateHandle,
    workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val worker = workerDispatcher
    private val first = savedStateHandle.getStateFlow(KEY_FIRST, "")
    private val second = savedStateHandle.getStateFlow(KEY_SECOND, "")
    private val ignoreCase = savedStateHandle.getStateFlow(KEY_CASE, false)
    private val ignoreWhitespace = savedStateHandle.getStateFlow(KEY_SPACE, false)
    private val rejected = MutableStateFlow(false)

    private data class Inputs(
        val first: String,
        val second: String,
        val ignoreCase: Boolean,
        val ignoreWhitespace: Boolean,
        val rejected: Boolean,
    )

    private val inputs = combine(first, second, ignoreCase, ignoreWhitespace) { a, b, case, space ->
        Inputs(a, b, case, space, false)
    }.combine(rejected) { inputs, tooLong -> inputs.copy(rejected = tooLong) }

    val uiState: StateFlow<SimilarityUiState> = inputs
        .map { evaluate(it) }
        .flowOn(worker)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            SimilarityUiState(first = first.value, second = second.value),
        )

    fun updateFirst(value: String) = update(KEY_FIRST, value)

    fun updateSecond(value: String) = update(KEY_SECOND, value)

    fun toggleIgnoreCase(value: Boolean) { savedStateHandle[KEY_CASE] = value }

    fun toggleIgnoreWhitespace(value: Boolean) { savedStateHandle[KEY_SPACE] = value }

    fun clear() {
        rejected.value = false
        savedStateHandle[KEY_FIRST] = ""
        savedStateHandle[KEY_SECOND] = ""
    }

    private fun update(key: String, value: String) {
        val tooLong = value.length > SimilarityLimits.MAX_INPUT_LENGTH
        rejected.value = tooLong
        if (!tooLong) savedStateHandle[key] = value
    }

    private fun evaluate(inputs: Inputs): SimilarityUiState {
        val base = SimilarityUiState(
            first = inputs.first,
            second = inputs.second,
            ignoreCase = inputs.ignoreCase,
            ignoreWhitespace = inputs.ignoreWhitespace,
        )
        if (inputs.rejected) return base.copy(outcome = SimilarityOutcome.Failure(SimilarityError.INPUT_TOO_LONG))
        if (inputs.first.isEmpty() || inputs.second.isEmpty()) return base
        return base.copy(
            outcome = SimilarityOutcome.Success(
                compareTexts(
                    inputs.first,
                    inputs.second,
                    SimilarityOptions(inputs.ignoreCase, inputs.ignoreWhitespace),
                ),
            ),
        )
    }

    companion object {
        private const val KEY_FIRST = "first"
        private const val KEY_SECOND = "second"
        private const val KEY_CASE = "case"
        private const val KEY_SPACE = "space"

        fun factory(compareTexts: CompareTextsUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return SimilarityViewModel(compareTexts, savedStateHandle) as T
                }
            }
    }
}
