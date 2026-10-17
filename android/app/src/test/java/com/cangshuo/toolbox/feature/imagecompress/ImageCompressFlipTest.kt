package com.cangshuo.toolbox.feature.imagecompress

import com.cangshuo.toolbox.feature.imagecompress.domain.FlipMode
import com.cangshuo.toolbox.feature.imagecompress.domain.scaleFactors
import com.cangshuo.toolbox.feature.imagecompress.domain.cornerRadiusPx
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCompressFlipTest {
    @Test fun flipModesMapToExpectedAxisScales() {
        assertEquals(1f to 1f, FlipMode.NONE.scaleFactors())
        assertEquals(-1f to 1f, FlipMode.HORIZONTAL.scaleFactors())
        assertEquals(1f to -1f, FlipMode.VERTICAL.scaleFactors())
        assertEquals(-1f to -1f, FlipMode.BOTH.scaleFactors())
    }

    @Test fun cornerRadiusUsesShortEdgeAndClampsToHalf() {
        assertEquals(0f, cornerRadiusPx(0, 1000, 800), 0f)
        assertEquals(200f, cornerRadiusPx(25, 1000, 800), 0f)
        assertEquals(400f, cornerRadiusPx(50, 1000, 800), 0f)
        assertEquals(400f, cornerRadiusPx(99, 1000, 800), 0f)
        assertEquals(0f, cornerRadiusPx(-1, 1000, 800), 0f)
    }
}
