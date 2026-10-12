package com.cangshuo.toolbox.feature.similarity.domain

class CompareTextsUseCase(private val repository: SimilarityRepository) {
    operator fun invoke(first: String, second: String, options: SimilarityOptions): SimilarityResult =
        repository.compare(first, second, options)
}
