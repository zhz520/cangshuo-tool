package com.cangshuo.toolbox.feature.colorpicker

import com.cangshuo.toolbox.feature.colorpicker.domain.ColorPickerLimits
import com.cangshuo.toolbox.feature.colorpicker.domain.PickedColor
import com.cangshuo.toolbox.feature.colorpicker.domain.SamplePoint
import com.cangshuo.toolbox.feature.colorpicker.domain.argbHex
import com.cangshuo.toolbox.feature.colorpicker.domain.argbHsl
import com.cangshuo.toolbox.feature.colorpicker.domain.argbRgb
import com.cangshuo.toolbox.feature.colorpicker.domain.fitImage
import com.cangshuo.toolbox.feature.colorpicker.domain.mapTouch
import com.cangshuo.toolbox.feature.colorpicker.domain.markerCenter
import com.cangshuo.toolbox.feature.colorpicker.domain.previewSampleSize
import com.cangshuo.toolbox.feature.colorpicker.domain.pushPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import com.cangshuo.toolbox.feature.colorpicker.domain.ColorPickerException
import com.cangshuo.toolbox.feature.colorpicker.domain.ColorPickerError
import com.cangshuo.toolbox.feature.colorpicker.domain.ImageFit
import org.junit.Test

class ColorPickerTest {

    @Test
    fun opaqueColoursUseSixDigitHexAndTransparencyAddsAlpha() {
        assertEquals("#FF8800", argbHex(0xFFFF8800.toInt()))
        assertEquals("#000000", argbHex(0xFF000000.toInt()))
        assertEquals("#FFFFFF", argbHex(0xFFFFFFFF.toInt()))
        assertEquals("#FF880080", argbHex(0x80FF8800.toInt()))
        assertEquals("#00000000", argbHex(0x00000000))
    }

    @Test
    fun rgbAndHslMatchTheColourConverterDisplayRules() {
        assertEquals("rgb(255, 136, 0)", argbRgb(0xFFFF8800.toInt()))
        assertEquals("hsl(32, 100%, 50%)", argbHsl(0xFFFF8800.toInt()))
        assertEquals("hsl(0, 0%, 0%)", argbHsl(0xFF000000.toInt()))
        assertEquals("hsl(0, 0%, 100%)", argbHsl(0xFFFFFFFF.toInt()))
        assertEquals("hsla(32, 100%, 50%, 0.251)", argbHsl(0x40FF8800))
        assertEquals("rgba(255, 136, 0, 0.502)", argbRgb(0x80FF8800.toInt()))
    }

    @Test
    fun hslWrapsNegativeHuesIntoZeroTo360() {
        // The red-dominant case with blue above green exercises the negative (g - b) branch.
        assertEquals("hsl(240, 100%, 50%)", argbHsl(0xFF0000FF.toInt()))
        assertEquals("hsl(0, 100%, 50%)", argbHsl(0xFFFF0000.toInt()))
        assertEquals("hsl(120, 100%, 50%)", argbHsl(0xFF00FF00.toInt()))
        assertEquals("hsl(330, 100%, 50%)", argbHsl(0xFFFF0080.toInt()))
    }

    @Test
    fun previewSampleSizeHalvesUntilBothEdgesFitTheBudget() {
        assertEquals(1, previewSampleSize(1024, 768))
        assertEquals(2, previewSampleSize(2048, 2048))
        assertEquals(2, previewSampleSize(2049, 100))
        assertEquals(8, previewSampleSize(8192, 8192))
        assertEquals(8, previewSampleSize(9000, 8000))
        assertEquals(1, previewSampleSize(10, 10, maxEdge = 16))
    }

    @Test
    fun fitAndMapHandleBothLetterboxAxesAndRejectOutsideTouches() {
        // Wide box, square image: bars on the left and right.
        val wide = requireNotNull(fitImage(200f, 100f, 100, 100))
        assertEquals(50f, wide.left, 0.0005f)
        assertEquals(0f, wide.top, 0.0005f)
        assertEquals(1f, wide.scale, 0.0005f)
        assertEquals(SamplePoint(0, 0, 0.005f, 0.005f), mapTouch(wide, 100, 100, 50f, 0f))
        assertEquals(SamplePoint(99, 99, 0.995f, 0.995f), mapTouch(wide, 100, 100, 149.9f, 99.9f))
        assertNull(mapTouch(wide, 100, 100, 49f, 50f))
        assertNull(mapTouch(wide, 100, 100, 150f, 50f))

        // Tall box, square image: bars on the top and bottom.
        val tall = requireNotNull(fitImage(100f, 200f, 100, 100))
        assertEquals(0f, tall.left, 0.0005f)
        assertEquals(50f, tall.top, 0.0005f)
        assertEquals(SamplePoint(50, 50, 0.505f, 0.505f), mapTouch(tall, 100, 100, 50f, 100f))
        assertNull(mapTouch(tall, 100, 100, 50f, 49f))
        assertNull(mapTouch(tall, 100, 100, 50f, 151f))
    }

    @Test
    fun fitRefusesDegenerateInputs() {
        assertNull(fitImage(0f, 100f, 100, 100))
        assertNull(fitImage(100f, 0f, 100, 100))
        assertNull(fitImage(100f, 100f, 0, 100))
        assertNull(fitImage(100f, 100f, 100, -1))
        assertNull(fitImage(Float.NaN, 100f, 100, 100))
        assertNull(fitImage(Float.POSITIVE_INFINITY, 100f, 100, 100))
    }

    @Test
    fun nonfiniteTouchesAndInvalidFitNeverBecomePixelZero() {
        val fit = ImageFit(0f, 0f, 1f)
        assertNull(mapTouch(fit, 100, 100, Float.NaN, 50f))
        assertNull(mapTouch(fit, 100, 100, 50f, Float.POSITIVE_INFINITY))
        assertNull(mapTouch(fit.copy(scale = 0f), 100, 100, 50f, 50f))
        assertNull(mapTouch(fit.copy(left = Float.NaN), 100, 100, 50f, 50f))
        assertNull(mapTouch(fit, 0, 100, 0f, 0f))
    }

    @Test
    fun decodeBudgetAccountsForCeilingAndLowMemoryBeforeAllocation() {
        assertEquals(2, previewSampleSize(4097, 100, maxEdge = 4096))
        assertEquals(2, previewSampleSize(2048, 2048, heapBytes = 128L * 1024 * 1024))
        assertEquals(ColorPickerError.TOO_LARGE,
            assertThrows(ColorPickerException::class.java) { previewSampleSize(100_001, 1) }.reason)
        assertEquals(ColorPickerError.TOO_LARGE,
            assertThrows(ColorPickerException::class.java) { previewSampleSize(50_000, 50_000) }.reason)
        assertEquals(ColorPickerError.MEMORY_LIMIT,
            assertThrows(ColorPickerException::class.java) { previewSampleSize(1, 1, heapBytes = 16L * 1024 * 1024) }.reason)
        assertThrows(IllegalArgumentException::class.java) { previewSampleSize(0, 10) }
        assertThrows(IllegalArgumentException::class.java) { previewSampleSize(10, 10, maxEdge = 0) }
    }

    @Test
    fun highBitDepthAndPreviousPreviewFitBudgetBeforeDecode() {
        for (heap in listOf(64L, 128L, 256L, 512L)) {
            val heapBytes = heap * 1024 * 1024
            val budget = minOf(ColorPickerLimits.MAX_DECODE_BYTES, heapBytes / 4)
            var previousBytes = 0L
            for ((width, height) in listOf(2048 to 2048, 1 to 1, 8192 to 8192, 4097 to 103)) {
                val sample = previewSampleSize(width, height, heapBytes = heapBytes)
                val w = (width.toLong() + sample - 1) / sample
                val h = (height.toLong() + sample - 1) / sample
                val currentBytes = w * h * 8 // RGBA_F16, not the requested ARGB_8888.
                org.junit.Assert.assertTrue(previousBytes + currentBytes * 2 + 8L * 1024 * 1024 <= budget)
                previousBytes = currentBytes
            }
        }
    }

    @Test
    fun samplingAndMarkerMatchComposeIntegerAlignmentAtOddViewportWidths() {
        val fit = requireNotNull(fitImage(301f, 240f, 1500, 1500))
        assertEquals(31f, fit.left, 0f)
        val center = requireNotNull(mapTouch(fit, 1500, 1500, 151.5f, 120f))
        assertEquals(753, center.x)
        assertEquals(750, center.y)
        assertEquals(1498, requireNotNull(mapTouch(fit, 1500, 1500, 270.75f, 120f)).x)
        assertNull(mapTouch(fit, 1500, 1500, 30.75f, 120f))
        assertNull(mapTouch(fit, 1500, 1500, 271f, 120f))
        val (x, y) = markerCenter(fit, SamplePoint(753, 750, 0f, 0f))
        assertEquals(151.56f, x, 0.001f)
        assertEquals(120.08f, y, 0.001f)
    }

    @Test
    fun nonSquareBitmapUsesEachRoundedDrawAxisForMapping() {
        val fit = requireNotNull(fitImage(301f, 240f, 1500, 1499))
        // ContentScale.Fit -> 240.1601 x 240, BitmapPainter -> 240 x 240.
        assertEquals(31f, fit.left, 0f)
        val point = requireNotNull(mapTouch(fit, 1500, 1499, 151.5f, 120f))
        assertEquals(753, point.x)
        assertEquals(749, point.y)
        assertNull(mapTouch(fit, 1500, 1499, 271f, 120f))
        assertNull(mapTouch(fit.copy(scaleY = Float.NaN), 1500, 1499, 151f, 120f))
    }

    @Test
    fun markerCentreUsesPixelCentreNotCorner() {
        val fit = requireNotNull(fitImage(200f, 100f, 100, 100))
        val (x, y) = markerCenter(fit, SamplePoint(0, 0, 0.005f, 0.005f))
        assertEquals(50.5f, x, 0.0005f)
        assertEquals(0.5f, y, 0.0005f)
    }

    @Test
    fun paletteKeepsNewestFirstDeduplicatesAndCaps() {
        val a = PickedColor(0xFFFF0000.toInt())
        val b = PickedColor(0xFF00FF00.toInt())
        val c = PickedColor(0xFF0000FF.toInt())
        var palette = pushPalette(emptyList(), a)
        palette = pushPalette(palette, b)
        assertEquals(listOf(b, a), palette)
        palette = pushPalette(palette, a)
        assertEquals(listOf(a, b), palette)
        repeat(ColorPickerLimits.MAX_PALETTE + 5) { index ->
            palette = pushPalette(palette, PickedColor(0xFF000000.toInt() or index))
        }
        assertEquals(ColorPickerLimits.MAX_PALETTE, palette.size)
        assertEquals(PickedColor(0xFF000000.toInt() or (ColorPickerLimits.MAX_PALETTE + 4)), palette.first())
        assertEquals(palette.map { it.argb }.distinct().size, palette.size)
        assertEquals(true, palette.none { it.argb == c.argb })
    }
}
