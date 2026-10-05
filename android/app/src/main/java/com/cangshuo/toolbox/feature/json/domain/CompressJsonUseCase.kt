package com.cangshuo.toolbox.feature.json.domain

class CompressJsonUseCase(private val repository: JsonRepository) {
    suspend operator fun invoke(input: String): JsonProcessResult {
        return repository.compress(input)
    }
}
