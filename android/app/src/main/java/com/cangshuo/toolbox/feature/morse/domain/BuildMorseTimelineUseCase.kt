package com.cangshuo.toolbox.feature.morse.domain

class BuildMorseTimelineUseCase(private val repository: MorseRepository) {
    operator fun invoke(text: String, direction: MorseDirection): List<MorseTone>? =
        repository.timeline(text, direction)
}
