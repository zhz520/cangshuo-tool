package com.cangshuo.toolbox.feature.qr.domain

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrCropPolicyTest {
    @Test
    fun fullRegionCoversTheShorterEdge() {
        // A wide image centers the square on the shorter edge instead of pinning it left.
        val wide = QrCropPolicy.rect(QrCropRegion(), 1000, 500)!!
        assertEquals(250, wide.left)
        assertEquals(0, wide.top)
        assertEquals(500, wide.side)
        val square = QrCropPolicy.rect(QrCropRegion(), 800, 800)!!
        assertEquals(0, square.left)
        assertEquals(0, square.top)
        assertEquals(800, square.side)
    }

    @Test
    fun centerAndFractionMapToPixels() {
        val rect = QrCropPolicy.rect(
            QrCropRegion(centerX = 0.25f, centerY = 0.5f, sideFraction = 0.4f), 1000, 500,
        )!!
        assertEquals(200, rect.side)
        assertEquals(150, rect.left)
        assertEquals(150, rect.top)
    }

    @Test
    fun selectionStaysInsideTheImageAtEveryCorner() {
        val topLeft = QrCropPolicy.rect(QrCropRegion(0f, 0f, 0.5f), 1000, 1000)!!
        assertEquals(0, topLeft.left)
        assertEquals(0, topLeft.top)
        val bottomRight = QrCropPolicy.rect(QrCropRegion(1f, 1f, 0.5f), 1000, 600)!!
        assertEquals(700, bottomRight.left)
        assertEquals(300, bottomRight.top)
    }

    @Test
    fun sideFractionIsClampedAndNonFiniteInputIsRejected() {
        val tiny = QrCropPolicy.rect(QrCropRegion(sideFraction = 0.001f), 1000, 1000)!!
        assertEquals((QrCropPolicy.MIN_SIDE_FRACTION * 1000).roundToInt(), tiny.side)
        val huge = QrCropPolicy.rect(QrCropRegion(sideFraction = 5f), 400, 900)!!
        assertEquals(400, huge.side)
        assertNull(QrCropPolicy.rect(QrCropRegion(sideFraction = Float.NaN), 100, 100))
        assertNull(QrCropPolicy.rect(QrCropRegion(centerX = Float.POSITIVE_INFINITY), 100, 100))
    }

    @Test
    fun degenerateImagesAreRejected() {
        assertNull(QrCropPolicy.rect(QrCropRegion(), 4, 100))
        assertNull(QrCropPolicy.rect(QrCropRegion(), 100, 0))
    }
}
