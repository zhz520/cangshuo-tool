package com.cangshuo.toolbox.feature.radix

import com.cangshuo.toolbox.feature.radix.data.LocalRadixRepository
import com.cangshuo.toolbox.feature.radix.domain.RadixError
import com.cangshuo.toolbox.feature.radix.domain.RadixResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadixConversionTest {

    private val repo = LocalRadixRepository()

    private fun success(input: String, radix: Int? = null, upper: Boolean = false): RadixResult.Success {
        val result = repo.convert(input, radix, upper)
        assertTrue("expected success for <$input> but was $result", result is RadixResult.Success)
        return result as RadixResult.Success
    }

    @Test
    fun decimalToCommonBases() {
        val result = success("255")
        assertEquals(10, result.input.radix)
        assertTrue(result.input.autoDetected)
        val values = result.outputs.associate { it.radix to it.value }
        assertEquals("11111111", values[2])
        assertEquals("377", values[8])
        assertEquals("255", values[10])
        assertEquals("ff", values[16])
        assertEquals("73", values[36])
        assertEquals(8, result.bitLength)
    }

    @Test fun signedPrefixesUseMagnitudeForBitAndByteStatistics() {
        val negative = success("-0xFF")
        assertEquals("-255", negative.normalized)
        assertEquals(8, negative.bitLength)
        assertEquals(1, negative.groupCount)
        assertEquals("-11111111", negative.outputs.first { it.radix == 2 }.value)
        assertEquals("15", success("+0o17").normalized)
        assertEquals("0", success("-0").normalized)
    }

    @Test fun whitespaceSeparatorsAndRawErrorPositionsAreConsistent() {
        assertEquals("255", success("1111\t1111", 2).normalized)
        assertEquals("255", success("F\r\nF", 16).normalized)
        assertEquals(7, (repo.convert("  -0xFg", null, false) as RadixResult.Error).position)
        assertEquals(4, (repo.convert("0b12", null, false) as RadixResult.Error).position)
    }

    @Test fun limitsAreCheckedBeforeParsingAndInvalidBasesHaveTheirOwnError() {
        assertEquals(RadixError.INPUT_TOO_LARGE, (repo.convert("1".repeat(10_001), 10, false) as RadixResult.Error).reason)
        assertEquals(RadixError.INPUT_TOO_LARGE, (repo.convert(" ".repeat(10_001), null, false) as RadixResult.Error).reason)
        assertEquals(RadixError.INVALID_RADIX, (repo.convert("1", -1, false) as RadixResult.Error).reason)
        assertEquals(RadixError.INVALID_RADIX, (repo.convert("1", 37, false) as RadixResult.Error).reason)
        assertEquals(10_000, success("1".repeat(10_000), 2).bitLength)
    }

    @Test fun emptySignedValuesDoNotPretendToBeZero() {
        for (value in listOf("+", "-", "-0x", "+0b", "'_,\t")) {
            assertEquals(RadixError.EMPTY, (repo.convert(value, null, false) as RadixResult.Error).reason)
        }
    }

    @Test
    fun hexadecimalInputAndUppercaseOutput() {
        val result = success("ff", 16, upper = true)
        assertEquals("255", result.normalized)
        val values = result.outputs.associate { it.radix to it.value }
        assertEquals("FF", values[16])
        assertEquals("11111111", values[2])
    }

    @Test
    fun prefixAutoDetectsBase() {
        assertEquals(16, success("0x10").input.radix)
        assertEquals("16", success("0x10").normalized)
        assertEquals(2, success("0b101").input.radix)
        assertEquals("5", success("0b101").normalized)
        assertEquals(8, success("0o17").input.radix)
        assertEquals("15", success("0o17").normalized)
    }

    @Test
    fun explicitBaseWinsOverPlainDigits() {
        // "11" in base 2 is 3, not 11.
        assertEquals("3", success("11", 2).normalized)
        // Prefix still wins when the caller passes null (auto), but an explicit base ignores it.
        assertEquals("0x10", success("0x10", null).input.text)
        assertEquals("16", success("0x10", null).normalized)
        // A matching explicit base keeps the prefix; a conflicting one validates the raw text.
        assertEquals("16", success("0x10", 16).normalized)
        val conflict = repo.convert("0x10", 10, false) as RadixResult.Error
        assertEquals(2, conflict.position)
    }

    @Test
    fun separatorsAndCaseAreAccepted() {
        assertEquals("255", success("1111 1111", 2).normalized)
        assertEquals("255", success("FF", 16).normalized)
        assertEquals("255", success("_FF", 16).normalized)
        assertEquals("255", success("F,F", 16).normalized)
    }

    @Test
    fun invalidCharacterReportsOneBasedPosition() {
        val result = repo.convert("12x4", 10, false)
        val error = result as RadixResult.Error
        assertEquals(RadixError.INVALID_CHAR, error.reason)
        assertEquals(3, error.position)
    }

    @Test
    fun digitOutsideRadixIsRejected() {
        val result = repo.convert("8", 8, false)
        assertEquals(1, (result as RadixResult.Error).position)
    }

    @Test
    fun emptyAndBlankInputsAreRejected() {
        assertEquals(RadixError.EMPTY, (repo.convert("", null, false) as RadixResult.Error).reason)
        assertEquals(RadixError.EMPTY, (repo.convert("   ", null, false) as RadixResult.Error).reason)
        assertEquals(RadixError.EMPTY, (repo.convert("0x", null, false) as RadixResult.Error).reason)
    }

    @Test
    fun hugeNumbersKeepArbitraryPrecision() {
        val big = "123456789012345678901234567890"
        val result = success(big)
        // Round-trip through base 16 and back.
        val hex = result.outputs.first { it.radix == 16 }.value
        assertEquals(big, success(hex, 16).normalized)
        assertTrue(result.bitLength > 63)
    }

    @Test
    fun maxBase36RoundTrip() {
        val result = success("zz", 36)
        val dec = result.normalized
        assertEquals("1295", dec)
        assertEquals("zz", success(dec, 10).outputs.first { it.radix == 36 }.value)
    }
}
