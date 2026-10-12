package com.cangshuo.toolbox.feature.numberchinese.domain

class ConvertNumberUseCase(private val repository: NumberChineseRepository) {
    operator fun invoke(
        input: String,
        style: ChineseNumberStyle,
        direction: NumberDirection,
        colloquialTen: Boolean,
    ): NumberChineseOutcome = repository.convert(input, style, direction, colloquialTen)

    fun detectDirection(input: String): NumberDirection = repository.detectDirection(input)
}
