package com.cangshuo.toolbox.feature.morse.domain

class EncodeMorseUseCase(private val repository: MorseRepository) {
    operator fun invoke(text: String): MorseResult = repository.encode(text)
}
