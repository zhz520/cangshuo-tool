package com.cangshuo.toolbox.feature.noise.data

import com.cangshuo.toolbox.feature.noise.domain.NoisePlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoisePlaybackRuntime {
    private val mutable = MutableStateFlow(NoisePlaybackState())
    val state: StateFlow<NoisePlaybackState> = mutable.asStateFlow()

    fun publish(state: NoisePlaybackState) {
        mutable.value = state
    }
}
