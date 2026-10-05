package com.cangshuo.toolbox.feature.json.domain

class FormatJsonUseCase(private val repository: JsonRepository) {
    suspend operator fun invoke(input: String, indent: JsonIndent): JsonProcessResult {
        return repository.format(input, indent)
    }
}
