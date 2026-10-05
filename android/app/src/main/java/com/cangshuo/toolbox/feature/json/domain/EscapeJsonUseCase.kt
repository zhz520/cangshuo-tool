package com.cangshuo.toolbox.feature.json.domain

class EscapeJsonUseCase(private val repository: JsonRepository) {
    suspend operator fun invoke(input: String): JsonProcessResult {
        return repository.escape(input)
    }
}
