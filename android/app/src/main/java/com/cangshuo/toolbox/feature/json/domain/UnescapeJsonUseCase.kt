package com.cangshuo.toolbox.feature.json.domain

class UnescapeJsonUseCase(private val repository: JsonRepository) {
    suspend operator fun invoke(input: String, stringInput: JsonStringInput = JsonStringInput.QUOTED_LITERAL): JsonProcessResult {
        return repository.unescape(input, stringInput)
    }
}
