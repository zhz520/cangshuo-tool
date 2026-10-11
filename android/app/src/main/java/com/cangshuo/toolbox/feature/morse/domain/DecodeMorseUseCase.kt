package com.cangshuo.toolbox.feature.morse.domain

class DecodeMorseUseCase(private val repository: MorseRepository) {
    operator fun invoke(code: String): MorseResult = repository.decode(code)
}
