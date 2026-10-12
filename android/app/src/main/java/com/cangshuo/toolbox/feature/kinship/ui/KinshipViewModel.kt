package com.cangshuo.toolbox.feature.kinship.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.kinship.domain.AskKinshipUseCase
import com.cangshuo.toolbox.feature.kinship.domain.KinshipError
import com.cangshuo.toolbox.feature.kinship.domain.KinshipLimits
import com.cangshuo.toolbox.feature.kinship.domain.KinshipOutcome
import com.cangshuo.toolbox.feature.kinship.domain.KinshipQuery
import com.cangshuo.toolbox.feature.kinship.domain.KinshipSex
import com.cangshuo.toolbox.feature.kinship.domain.KinshipType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class KinshipViewModel(
    private val askKinship: AskKinshipUseCase,
    private val savedStateHandle: SavedStateHandle,
    workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val worker = workerDispatcher

    private val text = savedStateHandle.getStateFlow(KEY_TEXT, "")
    private val target = savedStateHandle.getStateFlow(KEY_TARGET, "")
    private val sexName = savedStateHandle.getStateFlow(KEY_SEX, KinshipSex.UNSPECIFIED.name)
    private val typeName = savedStateHandle.getStateFlow(KEY_TYPE, KinshipType.TITLE.name)
    private val reverse = savedStateHandle.getStateFlow(KEY_REVERSE, false)
    private val optimal = savedStateHandle.getStateFlow(KEY_OPTIMAL, false)
    private val rejected = MutableStateFlow(false)

    private data class Inputs(
        val text: String,
        val target: String,
        val sexName: String,
        val typeName: String,
        val reverse: Boolean,
        val optimal: Boolean,
        val rejected: Boolean,
    )

    private data class TextInputs(
        val text: String,
        val target: String,
        val sexName: String,
        val typeName: String,
    )

    private data class Flags(val reverse: Boolean, val optimal: Boolean)

    private val textInputs = combine(text, target, sexName, typeName) { textValue, targetValue, sex, type ->
        TextInputs(textValue, targetValue, sex, type)
    }
    private val flags = combine(reverse, optimal) { reverseValue, optimalValue ->
        Flags(reverseValue, optimalValue)
    }
    private val inputs = combine(textInputs, flags) { values, options ->
        Inputs(values.text, values.target, values.sexName, values.typeName, options.reverse, options.optimal, false)
    }.combine(rejected) { inputs, tooLong -> inputs.copy(rejected = tooLong) }

    val uiState: StateFlow<KinshipUiState> = inputs
        .map { evaluate(it) }
        .flowOn(worker)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            KinshipUiState(text = text.value, target = target.value, reverse = reverse.value, optimal = optimal.value),
        )

    fun updateText(value: String) = update(KEY_TEXT, value)

    fun updateTarget(value: String) = update(KEY_TARGET, value)

    fun appendRelation(name: String) {
        rejected.value = false
        val current = text.value
        savedStateHandle[KEY_TEXT] = if (current.isEmpty()) name else "${current}的${name}"
    }

    fun removeLastRelation() {
        rejected.value = false
        val current = text.value
        savedStateHandle[KEY_TEXT] = when {
            current.isEmpty() -> current
            current.contains('的') -> current.substringBeforeLast('的')
            else -> ""
        }
    }

    fun selectSex(sex: KinshipSex) { savedStateHandle[KEY_SEX] = sex.name }

    fun selectType(type: KinshipType) { savedStateHandle[KEY_TYPE] = type.name }

    fun toggleReverse(value: Boolean) { savedStateHandle[KEY_REVERSE] = value }

    fun toggleOptimal(value: Boolean) { savedStateHandle[KEY_OPTIMAL] = value }

    fun clear() {
        rejected.value = false
        savedStateHandle[KEY_TEXT] = ""
        savedStateHandle[KEY_TARGET] = ""
    }

    private fun update(key: String, value: String) {
        val tooLong = value.length > KinshipLimits.MAX_INPUT_LENGTH
        rejected.value = tooLong
        if (!tooLong) savedStateHandle[key] = value
    }

    private fun evaluate(inputs: Inputs): KinshipUiState {
        val sex = KinshipSex.entries.firstOrNull { it.name == inputs.sexName } ?: KinshipSex.UNSPECIFIED
        val type = KinshipType.entries.firstOrNull { it.name == inputs.typeName } ?: KinshipType.TITLE
        val base = KinshipUiState(
            text = inputs.text,
            target = inputs.target,
            sex = sex,
            type = type,
            reverse = inputs.reverse,
            optimal = inputs.optimal,
        )
        if (inputs.rejected) return base.copy(outcome = KinshipOutcome.Failure(KinshipError.INPUT_TOO_LONG))
        if (inputs.text.isBlank() && inputs.target.isBlank()) return base
        return base.copy(
            outcome = KinshipOutcome.Success(
                askKinship(
                    KinshipQuery(
                        text = inputs.text,
                        target = inputs.target,
                        sex = sex,
                        type = type,
                        reverse = inputs.reverse,
                        optimal = inputs.optimal,
                    ),
                ),
            ),
        )
    }

    companion object {
        private const val KEY_TEXT = "text"
        private const val KEY_TARGET = "target"
        private const val KEY_SEX = "sex"
        private const val KEY_TYPE = "type"
        private const val KEY_REVERSE = "reverse"
        private const val KEY_OPTIMAL = "optimal"

        fun factory(askKinship: AskKinshipUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return KinshipViewModel(askKinship, savedStateHandle) as T
                }
            }
    }
}
