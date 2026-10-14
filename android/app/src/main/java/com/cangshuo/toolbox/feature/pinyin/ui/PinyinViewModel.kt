package com.cangshuo.toolbox.feature.pinyin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.pinyin.domain.ConvertToPinyinUseCase
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinCaseMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinError
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinLimits
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinOutcome
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinSeparator
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinToneStyle
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinVCharType
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives one conversion at a time: every edit cancels the running job and restarts after a short
 * debounce, and a revision guard drops results whose input or options changed in the meantime.
 */
class PinyinViewModel(
    private val convertToPinyin: ConvertToPinyinUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) : ViewModel() {

    private val mutableState = MutableStateFlow(restoredState())
    val uiState: StateFlow<PinyinUiState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var revision = 0L

    init {
        if (mutableState.value.input.isNotEmpty()) schedule()
    }

    fun updateInput(value: String) {
        if (value.length > PinyinLimits.MAX_INPUT_LENGTH) {
            invalidate()
            mutableState.value = mutableState.value.copy(
                processing = false,
                cancelled = false,
                outcome = PinyinOutcome.Failure(PinyinError.INPUT_TOO_LONG),
            )
            return
        }
        savedStateHandle[KEY_INPUT] = value
        change { copy(input = value) }
    }

    fun selectMode(mode: PinyinMode) = change { copy(mode = mode) }

    fun selectToneStyle(style: PinyinToneStyle) = change { copy(toneStyle = style) }

    fun selectCaseMode(mode: PinyinCaseMode) = change { copy(caseMode = mode) }

    fun selectSeparator(separator: PinyinSeparator) = change { copy(separator = separator) }

    fun selectVCharType(type: PinyinVCharType) = change { copy(vCharType = type) }

    fun toggleKeepNonChinese(keep: Boolean) {
        savedStateHandle[KEY_KEEP_NON_CHINESE] = keep
        change { copy(keepNonChinese = keep) }
    }

    /** Replaces the input with a bundled example and converts it immediately. */
    fun useExample(text: String) = updateInput(text)

    fun clear() {
        savedStateHandle[KEY_INPUT] = ""
        invalidate()
        mutableState.value = mutableState.value.copy(
            input = "",
            processing = false,
            cancelled = false,
            outcome = null,
        )
    }

    fun cancel() {
        invalidate()
        mutableState.value = mutableState.value.copy(processing = false, cancelled = true, outcome = null)
    }

    fun retry() {
        if (mutableState.value.input.isNotEmpty()) schedule()
    }

    private fun change(edit: PinyinUiState.() -> PinyinUiState) {
        mutableState.value = mutableState.value.edit().copy(processing = false, cancelled = false, outcome = null)
        persist(mutableState.value)
        schedule()
    }

    private fun persist(state: PinyinUiState) {
        savedStateHandle[KEY_INPUT] = state.input
        savedStateHandle[KEY_MODE] = state.mode.name
        savedStateHandle[KEY_TONE_STYLE] = state.toneStyle.name
        savedStateHandle[KEY_CASE_MODE] = state.caseMode.name
        savedStateHandle[KEY_SEPARATOR] = state.separator.name
        savedStateHandle[KEY_V_CHAR] = state.vCharType.name
        savedStateHandle[KEY_KEEP_NON_CHINESE] = state.keepNonChinese
    }

    private fun schedule() {
        invalidate()
        val state = mutableState.value
        if (state.input.isEmpty()) {
            mutableState.value = state.copy(processing = false, cancelled = false, outcome = null)
            return
        }
        val owner = revision
        mutableState.value = state.copy(processing = true, cancelled = false, outcome = null)
        job = viewModelScope.launch {
            try {
                delay(debounceMillis)
                val outcome = convertToPinyin(state.input, state.options)
                if (revision == owner) {
                    mutableState.value = mutableState.value.copy(processing = false, outcome = outcome)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (revision == owner) {
                    mutableState.value = mutableState.value.copy(
                        processing = false,
                        outcome = PinyinOutcome.Failure(PinyinError.PROCESSING_FAILED),
                    )
                }
            } finally {
                if (revision == owner && mutableState.value.processing) {
                    mutableState.value = mutableState.value.copy(processing = false)
                }
            }
        }
    }

    private fun invalidate() {
        revision++
        job?.cancel()
        job = null
    }

    private fun restoredState() = PinyinUiState(
        input = savedStateHandle.get<String>(KEY_INPUT)
            ?.takeIf { it.length <= PinyinLimits.MAX_INPUT_LENGTH } ?: "",
        mode = enumOrDefault(savedStateHandle[KEY_MODE], PinyinMode.entries, PinyinMode.FULL),
        toneStyle = enumOrDefault(
            savedStateHandle[KEY_TONE_STYLE],
            PinyinToneStyle.entries,
            PinyinToneStyle.TONE_MARK,
        ),
        caseMode = enumOrDefault(savedStateHandle[KEY_CASE_MODE], PinyinCaseMode.entries, PinyinCaseMode.LOWER),
        separator = enumOrDefault(
            savedStateHandle[KEY_SEPARATOR],
            PinyinSeparator.entries,
            PinyinSeparator.SPACE,
        ),
        vCharType = enumOrDefault(
            savedStateHandle[KEY_V_CHAR],
            PinyinVCharType.entries,
            PinyinVCharType.UNICODE,
        ),
        keepNonChinese = savedStateHandle[KEY_KEEP_NON_CHINESE] ?: true,
    )

    private fun <T : Enum<T>> enumOrDefault(name: String?, values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == name } ?: fallback

    companion object {
        private const val KEY_INPUT = "input"
        private const val KEY_MODE = "mode"
        private const val KEY_TONE_STYLE = "tone_style"
        private const val KEY_CASE_MODE = "case_mode"
        private const val KEY_SEPARATOR = "separator"
        private const val KEY_V_CHAR = "v_char"
        private const val KEY_KEEP_NON_CHINESE = "keep_non_chinese"
        internal const val DEFAULT_DEBOUNCE_MILLIS = 150L

        fun factory(convertToPinyin: ConvertToPinyinUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return PinyinViewModel(convertToPinyin, savedStateHandle) as T
                }
            }
    }
}
