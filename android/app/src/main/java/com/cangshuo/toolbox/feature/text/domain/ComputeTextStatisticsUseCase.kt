package com.cangshuo.toolbox.feature.text.domain

class ComputeTextStatisticsUseCase(private val repository: TextRepository) {
    suspend operator fun invoke(input: String): Result<TextStatistics> = repository.computeStatistics(input)
}
