package com.cangshuo.toolbox.feature.password.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.password.domain.GeneratePasswordsUseCase
import com.cangshuo.toolbox.feature.password.domain.PasswordError
import com.cangshuo.toolbox.feature.password.domain.PasswordLimits
import com.cangshuo.toolbox.feature.password.domain.PasswordOptions
import com.cangshuo.toolbox.feature.password.domain.PasswordOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class PasswordViewModel(
    private val generatePasswords: GeneratePasswordsUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val lengthText = savedStateHandle.getStateFlow(KEY_LENGTH, "16")
    private val countText = savedStateHandle.getStateFlow(KEY_COUNT, "1")
    private val uppercase = savedStateHandle.getStateFlow(KEY_UPPER, true)
    private val lowercase = savedStateHandle.getStateFlow(KEY_LOWER, true)
    private val digits = savedStateHandle.getStateFlow(KEY_DIGITS, true)
    private val symbols = savedStateHandle.getStateFlow(KEY_SYMBOLS, true)
    private val excludeSimilar = savedStateHandle.getStateFlow(KEY_SIMILAR, true)
    private val requireEachClass = savedStateHandle.getStateFlow(KEY_EACH, true)
    private val uniqueCharacters = savedStateHandle.getStateFlow(KEY_UNIQUE, false)
    private val excludeText = savedStateHandle.getStateFlow(KEY_EXCLUDE, "")
    private val regenerateNonce = MutableStateFlow(0)
    private val rejected = MutableStateFlow(false)

    private data class Toggles(
        val uppercase: Boolean,
        val lowercase: Boolean,
        val digits: Boolean,
        val symbols: Boolean,
        val excludeSimilar: Boolean,
        val requireEachClass: Boolean,
        val uniqueCharacters: Boolean,
    )

    private val texts = combine(lengthText, countText, excludeText) { length, count, exclude ->
        Triple(length, count, exclude)
    }
    private val toggles = combine(
        uppercase, lowercase, digits, symbols, excludeSimilar, requireEachClass, uniqueCharacters,
    ) { values ->
        Toggles(values[0], values[1], values[2], values[3], values[4], values[5], values[6])
    }
    private val nonce = combine(regenerateNonce, rejected) { nonceValue, isRejected ->
        nonceValue to isRejected
    }

    val uiState: StateFlow<PasswordUiState> = combine(texts, toggles, nonce) { text, toggle, state ->
        buildState(text.first, text.second, text.third, toggle, state.second)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        buildState(
            lengthText.value, countText.value, excludeText.value,
            Toggles(
                uppercase = uppercase.value, lowercase = lowercase.value, digits = digits.value,
                symbols = symbols.value, excludeSimilar = excludeSimilar.value,
                requireEachClass = requireEachClass.value, uniqueCharacters = uniqueCharacters.value,
            ),
            rejected.value,
        ),
    )

    fun updateLength(text: String) = updateText(KEY_LENGTH, text)

    fun updateCount(text: String) = updateText(KEY_COUNT, text)

    fun updateExclude(text: String) = updateText(KEY_EXCLUDE, text, PasswordLimits.MAX_EXCLUDE_LENGTH)

    fun toggleUppercase(value: Boolean) { savedStateHandle[KEY_UPPER] = value }

    fun toggleLowercase(value: Boolean) { savedStateHandle[KEY_LOWER] = value }

    fun toggleDigits(value: Boolean) { savedStateHandle[KEY_DIGITS] = value }

    fun toggleSymbols(value: Boolean) { savedStateHandle[KEY_SYMBOLS] = value }

    fun toggleExcludeSimilar(value: Boolean) { savedStateHandle[KEY_SIMILAR] = value }

    fun toggleRequireEachClass(value: Boolean) { savedStateHandle[KEY_EACH] = value }

    fun toggleUniqueCharacters(value: Boolean) { savedStateHandle[KEY_UNIQUE] = value }

    fun regenerate() { regenerateNonce.value += 1 }

    private fun updateText(key: String, text: String, maxLength: Int = 8) {
        val tooLong = text.length > maxLength
        rejected.value = tooLong
        if (!tooLong) savedStateHandle[key] = text
    }

    private fun buildState(
        length: String,
        count: String,
        exclude: String,
        toggles: Toggles,
        tooLong: Boolean,
    ): PasswordUiState {
        val base = PasswordUiState(
            lengthText = length,
            countText = count,
            uppercase = toggles.uppercase,
            lowercase = toggles.lowercase,
            digits = toggles.digits,
            symbols = toggles.symbols,
            excludeSimilar = toggles.excludeSimilar,
            requireEachClass = toggles.requireEachClass,
            uniqueCharacters = toggles.uniqueCharacters,
            excludeText = exclude,
        )
        if (tooLong) return base.copy(outcome = PasswordOutcome.Failure(PasswordError.INPUT_TOO_LONG))
        val lengthValue = length.trim().toIntOrNull()
        val countValue = count.trim().toIntOrNull()
        if (lengthValue == null || lengthValue !in PasswordLimits.MIN_LENGTH..PasswordLimits.MAX_LENGTH) {
            return base.copy(outcome = PasswordOutcome.Failure(PasswordError.LENGTH_RANGE))
        }
        if (countValue == null || countValue !in PasswordLimits.MIN_COUNT..PasswordLimits.MAX_COUNT) {
            return base.copy(outcome = PasswordOutcome.Failure(PasswordError.COUNT_RANGE))
        }
        val options = PasswordOptions(
            length = lengthValue,
            uppercase = toggles.uppercase,
            lowercase = toggles.lowercase,
            digits = toggles.digits,
            symbols = toggles.symbols,
            excludeSimilar = toggles.excludeSimilar,
            excludeText = exclude,
            requireEachClass = toggles.requireEachClass,
            uniqueCharacters = toggles.uniqueCharacters,
        )
        return base.copy(outcome = generatePasswords(options, countValue))
    }

    companion object {
        private const val KEY_LENGTH = "length"
        private const val KEY_COUNT = "count"
        private const val KEY_UPPER = "upper"
        private const val KEY_LOWER = "lower"
        private const val KEY_DIGITS = "digits"
        private const val KEY_SYMBOLS = "symbols"
        private const val KEY_SIMILAR = "similar"
        private const val KEY_EACH = "each"
        private const val KEY_UNIQUE = "unique"
        private const val KEY_EXCLUDE = "exclude"

        fun factory(generatePasswords: GeneratePasswordsUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return PasswordViewModel(generatePasswords, savedStateHandle) as T
                }
            }
    }
}
