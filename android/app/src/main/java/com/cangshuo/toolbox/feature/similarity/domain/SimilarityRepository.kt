package com.cangshuo.toolbox.feature.similarity.domain

interface SimilarityRepository {
    fun compare(first: String, second: String, options: SimilarityOptions): SimilarityResult
}
