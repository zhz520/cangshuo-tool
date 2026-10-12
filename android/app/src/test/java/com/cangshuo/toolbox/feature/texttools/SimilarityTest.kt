package com.cangshuo.toolbox.feature.texttools

import com.cangshuo.toolbox.feature.similarity.data.LocalSimilarityRepository
import com.cangshuo.toolbox.feature.similarity.domain.CompareTextsUseCase
import com.cangshuo.toolbox.feature.similarity.domain.SimilarityOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityTest {
    private val compare = CompareTextsUseCase(LocalSimilarityRepository())
    private val plain = SimilarityOptions()

    @Test
    fun classicalLevenshteinDistances() {
        assertEquals(3, compare("kitten", "sitting", plain).distance)
        assertEquals(2, compare("book", "back", plain).distance)
        assertEquals(0, compare("same", "same", plain).distance)
        assertTrue(compare("same", "same", plain).identical)
        assertFalse(compare("same", "same!", plain).identical)
    }

    @Test
    fun similarityAndSetOverlapAreNormalised() {
        val result = compare("abc", "abd", plain)
        assertEquals(1, result.distance)
        assertEquals(66.7, result.similarityPercent, 0.05)
        assertEquals(50.0, result.jaccardPercent, 0.05)
        assertEquals(2, result.commonPrefix)
        assertEquals(2, result.longestCommonSubstring)
    }

    @Test
    fun optionsIgnoreCaseAndWhitespace() {
        val options = SimilarityOptions(ignoreCase = true, ignoreWhitespace = true)
        val result = compare("Hel lo", "hello", options)
        assertTrue(result.identical)
        assertEquals(100.0, result.similarityPercent, 0.0)
    }

    @Test
    fun chineseTextsUseCharacterDistances() {
        val result = compare("前缀abc结尾", "前缀xyz结尾", plain)
        assertEquals(3, result.distance)
        assertEquals(2, result.commonPrefix)
        assertEquals(2, result.longestCommonSubstring)
    }
}
