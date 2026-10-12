package com.cangshuo.toolbox.feature.color

import com.cangshuo.toolbox.feature.color.data.LocalColorRepository
import com.cangshuo.toolbox.feature.color.domain.ColorError
import com.cangshuo.toolbox.feature.color.domain.ColorParseResult
import com.cangshuo.toolbox.feature.color.domain.HslColor
import com.cangshuo.toolbox.feature.color.domain.HsvColor
import com.cangshuo.toolbox.feature.color.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorConversionTest {
    private val repository = LocalColorRepository()

    private fun parsed(input: String) =
        (repository.parse(input) as ColorParseResult.Success).color

    private fun error(input: String) =
        (repository.parse(input) as ColorParseResult.Failure).error

    @Test
    fun hexAcceptsThreeAndSixDigitsWithOrWithoutHash() {
        assertEquals(RgbColor(170, 187, 204), parsed("#abc"))
        assertEquals(RgbColor(170, 187, 204), parsed("abc"))
        assertEquals(RgbColor(255, 136, 0), parsed("#FF8800"))
        assertEquals("#FF8800", repository.hex(RgbColor(255, 136, 0)))
        assertEquals("#000000", repository.hex(RgbColor(0, 0, 0)))
    }

    @Test
    fun hexRejectsInvalidDigitsAndAlpha() {
        assertEquals(ColorError.INVALID_FORMAT, error("#GG0000"))
        assertEquals(ColorError.INVALID_FORMAT, error("#12345"))
        assertEquals(ColorError.UNSUPPORTED_ALPHA, error("#FF880080"))
    }

    @Test
    fun rgbAcceptsFunctionsAndBareTriples() {
        assertEquals(RgbColor(255, 136, 0), parsed("rgb(255,136,0)"))
        assertEquals(RgbColor(255, 136, 0), parsed("rgb(255 136 0)"))
        assertEquals(RgbColor(255, 136, 0), parsed("255,136,0"))
        assertEquals(ColorError.OUT_OF_RANGE, error("rgb(256,0,0)"))
        assertEquals(ColorError.INVALID_FORMAT, error("rgb(1,2)"))
        assertEquals(ColorError.INVALID_FORMAT, error("rgb(50%,0,0)"))
    }

    @Test
    fun rgbToHslAndHsvMatchesCommonTools() {
        val red = RgbColor(255, 0, 0)
        assertEquals("hsl(0, 100%, 50%)", repository.hsl(red))
        assertEquals("hsv(0, 100%, 100%)", repository.hsv(red))
        assertEquals("hsl(120, 100%, 50%)", repository.hsl(RgbColor(0, 255, 0)))
        assertEquals("hsl(240, 100%, 50%)", repository.hsl(RgbColor(0, 0, 255)))
        val orange = repository.describe(RgbColor(255, 136, 0))
        assertEquals("hsl(32, 100%, 50%)", orange.hsl)
        assertEquals("hsv(32, 100%, 100%)", orange.hsv)
        assertEquals("rgb(255, 136, 0)", orange.rgb)
    }

    @Test
    fun greysKeepZeroSaturation() {
        val grey = repository.toHsl(RgbColor(128, 128, 128))
        assertEquals(0.0, grey.saturation, 1e-9)
        assertEquals("hsl(0, 0%, 50.2%)", repository.hsl(RgbColor(128, 128, 128)))
        assertEquals("hsl(0, 0%, 100%)", repository.hsl(RgbColor(255, 255, 255)))
    }

    @Test
    fun hslAndHsvInputsConvertBackToRgb() {
        assertEquals(RgbColor(255, 136, 0), parsed("hsl(32,100%,50%)"))
        assertEquals(RgbColor(255, 136, 0), parsed("hsv(32,100%,100%)"))
        assertEquals(RgbColor(255, 136, 0), parsed("hsl(392, 100%, 50%)"))
        assertEquals(ColorError.OUT_OF_RANGE, error("hsv(10,101%,50%)"))
    }

    @Test
    fun roundTripsStayWithinOneChannelStep() {
        val samples = listOf(
            RgbColor(255, 136, 0),
            RgbColor(18, 52, 86),
            RgbColor(120, 200, 30),
            RgbColor(7, 7, 7),
            RgbColor(240, 12, 200),
        )
        for (sample in samples) {
            val viaHsl = repository.fromHsl(repository.toHsl(sample))
            val viaHsv = repository.fromHsv(repository.toHsv(sample))
            assertTrue("hsl round trip strayed for $sample", close(sample, viaHsl))
            assertTrue("hsv round trip strayed for $sample", close(sample, viaHsv))
        }
    }

    @Test
    fun hueWrapsAndInvalidTextIsRejected() {
        val wrapped = repository.fromHsl(HslColor(hue = 360.0, saturation = 100.0, lightness = 50.0))
        assertEquals(RgbColor(255, 0, 0), wrapped)
        assertEquals(ColorError.INVALID_FORMAT, error("hello"))
        assertEquals(ColorError.INVALID_FORMAT, error(""))
        assertEquals(
            HsvColor(0.0, 0.0, 100.0),
            repository.toHsv(RgbColor(255, 255, 255)),
        )
    }

    private fun close(first: RgbColor, second: RgbColor): Boolean =
        kotlin.math.abs(first.red - second.red) <= 1 &&
            kotlin.math.abs(first.green - second.green) <= 1 &&
            kotlin.math.abs(first.blue - second.blue) <= 1
}
