package com.cangshuo.toolbox.feature.uuid.domain

class GenerateUuidUseCase(private val repository: UuidRepository) {
    fun generateRaw(count: Int): List<String> = repository.generateRaw(count)

    fun format(raw: String, config: UuidConfig): String = repository.format(raw, config)
}
