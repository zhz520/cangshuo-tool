package com.cangshuo.toolbox.feature.morse.ui

import com.cangshuo.toolbox.feature.morse.domain.MorseDirection
import com.cangshuo.toolbox.feature.morse.domain.MorseResult

data class MorseUiState(
    val inputText: String = "",
    val direction: MorseDirection = MorseDirection.ENCODE,
    val wpm: Int = DEFAULT_WPM,
    val isPlaying: Boolean = false,
    val result: MorseResult? = null,
) {
    companion object {
        const val DEFAULT_WPM = 15
    }
}
