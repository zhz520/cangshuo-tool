package com.cangshuo.toolbox.feature.radix.domain

class ConvertRadixUseCase(private val repository: RadixRepository) {
    operator fun invoke(rawInput: String, explicitRadix: Int?, uppercase: Boolean): RadixResult =
        repository.convert(rawInput, explicitRadix, uppercase)
}
