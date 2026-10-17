package com.cangshuo.toolbox.feature.banner

import com.cangshuo.toolbox.feature.banner.domain.BannerDirection
import com.cangshuo.toolbox.feature.banner.domain.BannerError
import com.cangshuo.toolbox.feature.banner.domain.BannerPolicy
import com.cangshuo.toolbox.feature.banner.domain.BannerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerPolicyTest {
    @Test fun inputFlattensControlsBoundsCodePointsAndPreservesEmojiSequences() {
        assertEquals("a b c ", BannerPolicy.sanitizeInput("a\nb\tc\r"))
        assertEquals("🚀", BannerPolicy.sanitizeInput("\u200B🚀\u2060"))
        assertEquals("👨‍👩‍👧", BannerPolicy.sanitizeInput("👨‍👩‍👧"))
        val long = "😀".repeat(BannerPolicy.MAX_TEXT_CODE_POINTS + 5)
        val sanitized = BannerPolicy.sanitizeInput(long)
        assertEquals(BannerPolicy.MAX_TEXT_CODE_POINTS, sanitized.codePointCount(0, sanitized.length))
        val family = "👨‍👩‍👧‍👦"
        val families = BannerPolicy.sanitizeInput(family.repeat(18))
        assertTrue(families.codePointCount(0, families.length) <= BannerPolicy.MAX_TEXT_CODE_POINTS)
        assertEquals(0, families.codePointCount(0, families.length) % family.codePointCount(0, family.length))
        val flags = BannerPolicy.sanitizeInput("🇨🇳".repeat(70))
        assertEquals(120, flags.codePointCount(0, flags.length))
        assertEquals(" Team ", BannerPolicy.sanitizeInput(" Team "))
    }

    @Test fun blankAndInvisibleOnlyTextCannotAreNotUsable() {
        assertFalse(BannerPolicy.isUsable(""))
        assertFalse(BannerPolicy.isUsable("   "))
        assertFalse(BannerPolicy.isUsable("\u200C\u200D"))
        assertTrue(BannerPolicy.isUsable(" A "))
    }

    @Test fun validationRequiresVisibleTextAndBoundedMotionSettings() {
        assertEquals(BannerError.EMPTY, BannerPolicy.validate(BannerSettings()))
        assertEquals(
            BannerError.OUT_OF_RANGE,
            BannerPolicy.validate(BannerSettings(text = "Hello", speedDpPerSecond = BannerPolicy.MAX_SPEED + 1f)),
        )
        assertEquals(
            BannerError.OUT_OF_RANGE,
            BannerPolicy.validate(BannerSettings(text = "Hello", fontSizeSp = BannerPolicy.MIN_FONT_SIZE - 1f)),
        )
        assertEquals(null, BannerPolicy.validate(BannerSettings(text = "Hello")))
    }

    @Test fun normalizationClampsSpeedAndFontWithoutDiscardingTextOrOptions() {
        val source = BannerSettings(
            text = "Hello",
            speedDpPerSecond = 999f,
            fontSizeSp = 1f,
            direction = BannerDirection.LEFT_TO_RIGHT,
            mirrored = true,
            keepScreenOn = false,
        )
        val normalized = BannerPolicy.normalize(source)
        assertEquals(BannerPolicy.MAX_SPEED, normalized.speedDpPerSecond)
        assertEquals(BannerPolicy.MIN_FONT_SIZE, normalized.fontSizeSp)
        assertEquals(BannerDirection.LEFT_TO_RIGHT, normalized.direction)
        assertTrue(normalized.mirrored)
        assertFalse(normalized.keepScreenOn)
    }

    @Test fun motionWrapsWithoutJumpsAndDirectionChangesTheEntryEdge() {
        val rtl0 = BannerPolicy.motionStartX(0f, 156f, 100f, 350f, BannerDirection.RIGHT_TO_LEFT)
        val rtlCycle = BannerPolicy.motionStartX(156f, 156f, 100f, 350f, BannerDirection.RIGHT_TO_LEFT)
        val ltr0 = BannerPolicy.motionStartX(0f, 156f, 100f, 350f, BannerDirection.LEFT_TO_RIGHT)
        assertEquals(350f, rtl0, 0.001f)
        assertEquals(350f, rtlCycle, 0.001f)
        assertEquals(-100f, ltr0, 0.001f)
        assertEquals(
            BannerPolicy.motionStartX(-10f, 156f, 100f, 350f, BannerDirection.RIGHT_TO_LEFT),
            BannerPolicy.motionStartX(146f, 156f, 100f, 350f, BannerDirection.RIGHT_TO_LEFT),
        )
    }

    @Test fun visibleCopiesCoverTheViewportAndRejectDegenerateGeometry() {
        val positions = BannerPolicy.visibleCopyPositions(38f, 156f, 100f, 350f)
        assertTrue(positions.size >= 2)
        assertTrue(positions.zipWithNext().all { (a, b) -> b - a == 156f })
        assertTrue(positions.last() < 350f)
        assertTrue(positions.first() + 100f > 0f)
        assertTrue(BannerPolicy.visibleCopyPositions(0f, 0f, 100f, 350f).isEmpty())
        assertTrue(BannerPolicy.visibleCopyPositions(Float.NaN, 156f, 100f, 350f).isEmpty())
    }

}
