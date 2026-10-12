package com.cangshuo.toolbox.feature.similarity.domain

data class SimilarityOptions(val ignoreCase: Boolean = false, val ignoreWhitespace: Boolean = false)

data class SimilarityResult(
    val identical: Boolean,
    val distance: Int,
    val similarityPercent: Double,
    val jaccardPercent: Double,
    val longestCommonSubstring: Int,
    val commonPrefix: Int,
)

enum class SimilarityError { INPUT_TOO_LONG }

object SimilarityLimits {
    const val MAX_INPUT_LENGTH = 3000
}

sealed interface SimilarityOutcome {
    data class Success(val result: SimilarityResult) : SimilarityOutcome

    data class Failure(val error: SimilarityError) : SimilarityOutcome
}
