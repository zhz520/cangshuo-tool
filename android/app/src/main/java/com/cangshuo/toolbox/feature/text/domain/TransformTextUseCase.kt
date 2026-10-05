package com.cangshuo.toolbox.feature.text.domain

class TransformTextUseCase(private val repository: TextRepository) {
    suspend operator fun invoke(input: String, operation: TextOperation): Result<String> = repository.transform(input, operation)
}
