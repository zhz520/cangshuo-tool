package com.cangshuo.toolbox.feature.password

import com.cangshuo.toolbox.feature.password.data.LocalPasswordRepository
import com.cangshuo.toolbox.feature.password.domain.GeneratePasswordsUseCase
import com.cangshuo.toolbox.feature.password.domain.PasswordError
import com.cangshuo.toolbox.feature.password.domain.PasswordLimits
import com.cangshuo.toolbox.feature.password.domain.PasswordOptions
import com.cangshuo.toolbox.feature.password.domain.PasswordOutcome
import com.cangshuo.toolbox.feature.password.domain.RandomSource
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    private val zeroRandom = RandomSource { 0 }
    private val generate = GeneratePasswordsUseCase(LocalPasswordRepository(zeroRandom))

    private fun options(
        length: Int = 16,
        upper: Boolean = true,
        lower: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
        similar: Boolean = true,
        exclude: String = "",
        each: Boolean = true,
        unique: Boolean = false,
    ) = PasswordOptions(length, upper, lower, digits, symbols, similar, exclude, each, unique)

    private fun failure(result: PasswordOutcome) = (result as PasswordOutcome.Failure).error

    private fun success(result: PasswordOutcome) = result as PasswordOutcome.Success

    @Test
    fun poolFollowsEnabledSetsAndAmbiguousExclusions() {
        val result = success(generate(options(length = 8, symbols = false), 1))
        assertEquals(26 + 26 + 10 - PasswordLimits.SIMILAR_CHARACTERS.count { it.isLetterOrDigit() }, result.poolSize)
        val password = result.passwords.single().value
        assertEquals(8, password.length)
        assertTrue(password.none { it in PasswordLimits.SIMILAR_CHARACTERS })
    }

    @Test
    fun strictModeGuaranteesEveryEnabledSet() {
        val password = success(generate(options(length = 12, similar = false), 1)).passwords.single().value
        assertTrue(password.any { it in 'a'..'z' })
        assertTrue(password.any { it in 'A'..'Z' })
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { it in PasswordLimits.SYMBOLS })
    }

    @Test
    fun exclusionsAndUniqueModeAreHonoured() {
        val excluded = success(generate(options(length = 6, lower = true, upper = false, digits = false, symbols = false, similar = false, exclude = "abc", each = false), 1))
        assertTrue(excluded.passwords.single().value.none { it in "abc" })
        val unique = success(generate(options(length = 10, upper = false, lower = false, digits = true, symbols = false, similar = false, each = false, unique = true), 1))
        assertEquals(10, unique.passwords.single().value.toSet().size)
        assertEquals(PasswordError.UNIQUE_IMPOSSIBLE, failure(generate(options(length = 12, upper = false, lower = false, digits = true, symbols = false, similar = false, each = false, unique = true), 1)))
    }

    @Test
    fun rangesAndEmptyPoolsAreRejected() {
        assertEquals(PasswordError.LENGTH_RANGE, failure(generate(options(length = 3), 1)))
        assertEquals(PasswordError.LENGTH_RANGE, failure(generate(options(length = 65), 1)))
        assertEquals(PasswordError.COUNT_RANGE, failure(generate(options(), 0)))
        assertEquals(PasswordError.COUNT_RANGE, failure(generate(options(), 11)))
        assertEquals(
            PasswordError.NO_CHARSET,
            failure(generate(options(upper = false, lower = false, digits = false, symbols = false), 1)),
        )
    }

    @Test
    fun entropyUsesLengthTimesLog2OfThePool() {
        val result = success(generate(options(length = 20, similar = false), 1))
        val expected = 20 * (ln(result.poolSize.toDouble()) / ln(2.0))
        assertEquals(expected, result.passwords.single().entropyBits, 1e-9)
    }

    @Test
    fun multiplePasswordsShareTheSameOptions() {
        val result = success(generate(options(length = 12), 3))
        assertEquals(3, result.passwords.size)
        assertTrue(result.passwords.all { it.value.length == 12 })
        assertEquals(4, success(generate(options(), 4)).passwords.size)
    }
}
