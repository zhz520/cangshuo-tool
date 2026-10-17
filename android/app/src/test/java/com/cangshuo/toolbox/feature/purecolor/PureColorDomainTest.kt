package com.cangshuo.toolbox.feature.purecolor

import com.cangshuo.toolbox.feature.purecolor.domain.*
import org.junit.Assert.*
import org.junit.Test

class PureColorDomainTest {
    @Test fun parsesThreeSixAndEightDigitHexWithOptionalHash() {
        assertEquals(0xFFFF8800.toInt(), parseHexColor("#F80"))
        assertEquals(0xFFFF8800.toInt(), parseHexColor("ff8800"))
        assertEquals(0x80FF8800.toInt(), parseHexColor("#FF880080"))
        assertEquals(0x00000000, parseHexColor("#00000000"))
    }

    @Test fun rejectsMalformedHexAndWhitespace() {
        listOf("", "#", "#12", "#GGGGGG", "#123456789").forEach {
            assertNull(it, parseHexColor(it))
        }
        assertEquals(0xFFFFFFFF.toInt(), parseHexColor(" #FFFFFF "))
    }

    @Test fun formatsCanonicalArgbAndFileName() {
        assertEquals("#FF8800", argbHex(0xFFFF8800.toInt()))
        assertEquals("#FF880080", argbHex(0x80FF8800.toInt()))
        assertEquals("pure-color-#FF8800-800x400.png", pureColorFileName(PureColorParams(0xFFFF8800.toInt(), 800, 400)))
    }

    @Test fun dimensionsAndMemoryAreValidatedBeforeBitmapAllocation() {
        validatePureColor(PureColorParams(0xFF000000.toInt(), 4096, 1), 256L * 1024 * 1024)
        assertEquals(PureColorError.INVALID_DIMENSION, reason(PureColorParams(0, 0, 1), 256L * 1024 * 1024))
        assertEquals(PureColorError.INVALID_DIMENSION, reason(PureColorParams(0, 1, 4097), 256L * 1024 * 1024))
        assertEquals(PureColorError.TOO_LARGE, reason(PureColorParams(0, 4096, 4096), 256L * 1024 * 1024))
        assertEquals(PureColorError.MEMORY_LIMIT, reason(PureColorParams(0, 2048, 2048), 8L * 1024 * 1024))
    }

    private fun reason(params: PureColorParams, heap: Long): PureColorError =
        assertThrows(PureColorException::class.java) { validatePureColor(params, heap) }.reason
}
