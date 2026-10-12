package com.cangshuo.toolbox.feature.similarity.data

import com.cangshuo.toolbox.feature.similarity.domain.SimilarityOptions
import com.cangshuo.toolbox.feature.similarity.domain.SimilarityRepository
import com.cangshuo.toolbox.feature.similarity.domain.SimilarityResult
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Levenshtein distance with the same cheap wins as the MIT-licensed sindresorhus/leven
 * implementation (swap so the first string is shorter, trim the common suffix then prefix,
 * then run one reused DP row); the remaining metrics are independent additions.
 */
class LocalSimilarityRepository : SimilarityRepository {

    override fun compare(first: String, second: String, options: SimilarityOptions): SimilarityResult {
        val a = normalize(first, options)
        val b = normalize(second, options)
        val identical = a == b
        val distance = if (identical) 0 else levenshtein(a, b)
        val maxLength = maxOf(a.length, b.length)
        val similarity = if (maxLength == 0) 100.0 else (1 - distance.toDouble() / maxLength) * 100
        val setA = a.toSet()
        val setB = b.toSet()
        val union = (setA + setB).size
        val intersection = (setA intersect setB).size
        val jaccard = if (union == 0) 100.0 else intersection.toDouble() / union * 100
        return SimilarityResult(
            identical = identical,
            distance = distance,
            similarityPercent = (similarity * 10).roundToInt() / 10.0,
            jaccardPercent = (jaccard * 10).roundToInt() / 10.0,
            longestCommonSubstring = longestCommonSubstring(a, b),
            commonPrefix = commonPrefix(a, b),
        )
    }

    private fun normalize(text: String, options: SimilarityOptions): String {
        var value = text
        if (options.ignoreWhitespace) value = value.filterNot { it.isWhitespace() }
        if (options.ignoreCase) value = value.lowercase(Locale.ROOT)
        return value
    }

    private fun levenshtein(firstIn: String, secondIn: String): Int {
        var first = firstIn
        var second = secondIn
        if (first.length > second.length) {
            val swap = first
            first = second
            second = swap
        }
        var firstLength = first.length
        var secondLength = second.length
        while (firstLength > 0 && first[firstLength - 1] == second[secondLength - 1]) {
            firstLength--
            secondLength--
        }
        var start = 0
        while (start < firstLength && first[start] == second[start]) start++
        firstLength -= start
        secondLength -= start
        if (firstLength == 0) return secondLength
        val row = IntArray(firstLength + 1) { it }
        for (column in 1..secondLength) {
            var previousDiagonal = row[0]
            row[0] = column
            for (i in 1..firstLength) {
                val previousRowValue = row[i]
                val cost = if (first[start + i - 1] == second[start + column - 1]) 0 else 1
                row[i] = minOf(row[i] + 1, row[i - 1] + 1, previousDiagonal + cost)
                previousDiagonal = previousRowValue
            }
        }
        return row[firstLength]
    }

    private fun longestCommonSubstring(first: String, second: String): Int {
        if (first.isEmpty() || second.isEmpty()) return 0
        var previous = IntArray(second.length + 1)
        var current = IntArray(second.length + 1)
        var best = 0
        for (i in 1..first.length) {
            for (j in 1..second.length) {
                current[j] = if (first[i - 1] == second[j - 1]) previous[j - 1] + 1 else 0
                if (current[j] > best) best = current[j]
            }
            val swap = previous
            previous = current
            current = swap
            current.fill(0)
        }
        return best
    }

    private fun commonPrefix(first: String, second: String): Int {
        val limit = minOf(first.length, second.length)
        var index = 0
        while (index < limit && first[index] == second[index]) index++
        return index
    }
}
