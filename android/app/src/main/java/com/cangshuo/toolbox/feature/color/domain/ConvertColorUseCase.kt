package com.cangshuo.toolbox.feature.color.domain

class ConvertColorUseCase(private val repository: ColorRepository) {
    fun parse(input: String): ColorParseResult = repository.parse(input)

    fun describe(color: RgbColor): ColorDescription = repository.describe(color)
}
