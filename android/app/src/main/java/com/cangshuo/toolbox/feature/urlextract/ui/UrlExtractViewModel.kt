package com.cangshuo.toolbox.feature.urlextract.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.urlextract.domain.ExtractUrlsUseCase
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractError
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractLimits
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractOptions
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractOutcome
import com.cangshuo.toolbox.feature.urlextract.domain.UrlOutputFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class UrlExtractViewModel(
    private val extractUrls: ExtractUrlsUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val text = savedStateHandle.getStateFlow(KEY_TEXT, "")
    private val includeBareDomains = savedStateHandle.getStateFlow(KEY_FUZZY, false)
    private val dedupe = savedStateHandle.getStateFlow(KEY_DEDUPE, true)
    private val formatName = savedStateHandle.getStateFlow(KEY_FORMAT, UrlOutputFormat.LINES.name)
    private val rejected = MutableStateFlow(false)

    val uiState: StateFlow<UrlExtractUiState> = combine(
        text, includeBareDomains, dedupe, formatName, rejected,
    ) { textValue, fuzzy, dedupeValue, formatValue, tooLong ->
        stateOf(textValue, fuzzy, dedupeValue, formatValue, tooLong)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        stateOf(text.value, includeBareDomains.value, dedupe.value, formatName.value, rejected.value),
    )

    private fun stateOf(
        textValue: String,
        fuzzy: Boolean,
        dedupeValue: Boolean,
        formatValue: String,
        tooLong: Boolean,
    ): UrlExtractUiState {
        val format = UrlOutputFormat.entries.firstOrNull { it.name == formatValue } ?: UrlOutputFormat.LINES
        val outcome = when {
            tooLong -> UrlExtractOutcome.Failure(UrlExtractError.INPUT_TOO_LONG)
            textValue.isBlank() -> null
            else -> UrlExtractOutcome.Success(
                extractUrls.extract(textValue, UrlExtractOptions(fuzzy, dedupeValue)),
            )
        }
        val copyText = (outcome as? UrlExtractOutcome.Success)
            ?.result
            ?.links
            ?.takeIf { it.isNotEmpty() }
            ?.let { extractUrls.format(it, format) }
            .orEmpty()
        return UrlExtractUiState(
            text = textValue,
            includeBareDomains = fuzzy,
            dedupe = dedupeValue,
            format = format,
            copyText = copyText,
            outcome = outcome,
        )
    }

    fun updateText(value: String) {
        val tooLong = value.length > UrlExtractLimits.MAX_INPUT_LENGTH
        rejected.value = tooLong
        if (!tooLong) savedStateHandle[KEY_TEXT] = value
    }

    fun toggleBareDomains(value: Boolean) { savedStateHandle[KEY_FUZZY] = value }

    fun toggleDedupe(value: Boolean) { savedStateHandle[KEY_DEDUPE] = value }

    fun selectFormat(format: UrlOutputFormat) { savedStateHandle[KEY_FORMAT] = format.name }

    fun clear() {
        rejected.value = false
        savedStateHandle[KEY_TEXT] = ""
    }

    companion object {
        private const val KEY_TEXT = "text"
        private const val KEY_FUZZY = "fuzzy"
        private const val KEY_DEDUPE = "dedupe"
        private const val KEY_FORMAT = "format"

        fun factory(extractUrls: ExtractUrlsUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return UrlExtractViewModel(extractUrls, savedStateHandle) as T
                }
            }
    }
}
