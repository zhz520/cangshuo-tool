package com.cangshuo.toolbox.feature.imagecompress

import com.cangshuo.toolbox.feature.imagecompress.domain.FlipMode
import com.cangshuo.toolbox.feature.imagecompress.domain.scaleFactors
import com.cangshuo.toolbox.feature.imagecompress.domain.cornerRadiusPx
import com.cangshuo.toolbox.feature.imagecompress.domain.mosaicCellSize
import com.cangshuo.toolbox.feature.imagecompress.domain.blurScaleFactor
import com.cangshuo.toolbox.feature.imagecompress.domain.RotationMode
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

    @Test fun mosaicCellUsesShortEdgeAndNeverReturnsZero() {
        assertEquals(1, mosaicCellSize(0, 1000, 800))
        assertEquals(40, mosaicCellSize(5, 1000, 800))
        assertEquals(160, mosaicCellSize(20, 1000, 800))
        assertEquals(400, mosaicCellSize(99, 1000, 800))
    }

    @Test fun blurFactorClampsToFiveDownsampleSteps() {
        assertEquals(1, blurScaleFactor(0))
        assertEquals(2, blurScaleFactor(4))
        assertEquals(3, blurScaleFactor(8))
        assertEquals(6, blurScaleFactor(20))
        assertEquals(6, blurScaleFactor(99))
    }

    @Test fun rotationModesExposeClockwiseDegrees() {
        assertEquals(0, RotationMode.NONE.degrees)
        assertEquals(90, RotationMode.ROTATE_90.degrees)
        assertEquals(180, RotationMode.ROTATE_180.degrees)
        assertEquals(270, RotationMode.ROTATE_270.degrees)
    }
}
