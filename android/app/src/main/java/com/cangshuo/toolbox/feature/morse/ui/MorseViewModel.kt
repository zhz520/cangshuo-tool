package com.cangshuo.toolbox.feature.morse.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.morse.data.MorseAudioPlayer
import com.cangshuo.toolbox.feature.morse.domain.BuildMorseTimelineUseCase
import com.cangshuo.toolbox.feature.morse.domain.DecodeMorseUseCase
import com.cangshuo.toolbox.feature.morse.domain.EncodeMorseUseCase
import com.cangshuo.toolbox.feature.morse.domain.MorseDirection
import com.cangshuo.toolbox.feature.morse.domain.MorseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MorseViewModel(
    private val encodeMorse: EncodeMorseUseCase,
    private val decodeMorse: DecodeMorseUseCase,
    private val buildTimeline: BuildMorseTimelineUseCase,
    private val player: MorseAudioPlayer,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val input = savedStateHandle.getStateFlow(KEY_INPUT, "")
    private val direction = savedStateHandle.getStateFlow(KEY_DIRECTION, MorseDirection.ENCODE.name)
    private val wpm = savedStateHandle.getStateFlow(KEY_WPM, MorseUiState.DEFAULT_WPM)
    private val playing = MutableStateFlow(false)

    val uiState: StateFlow<MorseUiState> = combine(
        input, direction, wpm, playing,
    ) { inText, dirName, speed, isPlaying ->
        val dir = runCatching { MorseDirection.valueOf(dirName) }.getOrDefault(MorseDirection.ENCODE)
        MorseUiState(
            inputText = inText,
            direction = dir,
            wpm = speed,
            isPlaying = isPlaying,
            result = when {
                inText.isBlank() -> null
                dir == MorseDirection.ENCODE -> encodeMorse(inText)
                else -> decodeMorse(inText)
            },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        MorseUiState(),
    )

    fun updateInput(text: String) {
        if (text.length <= MAX_INPUT_LENGTH) {
            savedStateHandle[KEY_INPUT] = text
        }
    }

    fun selectDirection(dir: MorseDirection) {
        if (dir.name != direction.value) {
            savedStateHandle[KEY_DIRECTION] = dir.name
        }
    }

    fun setWpm(value: Int) {
        savedStateHandle[KEY_WPM] = value.coerceIn(MorseAudioPlayer.MIN_WPM, MorseAudioPlayer.MAX_WPM)
    }

    fun play() {
        if (playing.value) {
            stop()
            return
        }
        val state = uiState.value
        val tones = buildTimeline(state.inputText, state.direction) ?: return
        playing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            player.play(tones, state.wpm) { playing.value = false }
        }
    }

    fun stop() {
        player.stop()
        playing.value = false
    }

    fun swap() {
        val state = uiState.value
        val result = state.result
        if (result !is MorseResult.Success || result.output.isEmpty()) return
        savedStateHandle[KEY_INPUT] = result.output
        savedStateHandle[KEY_DIRECTION] = if (state.direction == MorseDirection.ENCODE) {
            MorseDirection.DECODE.name
        } else {
            MorseDirection.ENCODE.name
        }
    }

    fun clear() {
        savedStateHandle[KEY_INPUT] = ""
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }

    companion object {
        private const val KEY_INPUT = "input"
        private const val KEY_DIRECTION = "direction"
        private const val KEY_WPM = "wpm"
        private const val MAX_INPUT_LENGTH = 10_000

        fun factory(
            encodeMorse: EncodeMorseUseCase,
            decodeMorse: DecodeMorseUseCase,
            buildTimeline: BuildMorseTimelineUseCase,
            player: MorseAudioPlayer,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val savedStateHandle = extras.createSavedStateHandle()
                return MorseViewModel(encodeMorse, decodeMorse, buildTimeline, player, savedStateHandle) as T
            }
        }
    }
}
