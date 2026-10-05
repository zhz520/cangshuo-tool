package com.cangshuo.toolbox.feature.qr.data

import com.google.zxing.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrBandDecoderTest {
    private val width = 480
    private val height = 360
    private val texts = listOf("BAND-A-001", "BAND-B-002", "BAND-C-003")

    @Test
    fun threeHorizontalBarcodesAreAllFound() {
        val frame = IntArray(width * height) { QrTestFrames.WHITE }
        texts.forEachIndexed { index, text ->
            QrTestFrames.horizontal(frame, width, text, BarcodeFormat.CODE_128,
                left = 80, top = 15 + index * 120, scale = 2, barHeight = 90)
        }
        val found = QrBandDecoder.decode(frame, width, height).map { it.text }
        assertTrue(found.containsAll(texts))
        assertEquals(found.size, found.distinct().size)
    }

    @Test
    fun threeVerticalBarcodesAreAllFound() {
        val frame = IntArray(width * height) { QrTestFrames.WHITE }
        texts.forEachIndexed { index, text ->
            QrTestFrames.vertical(frame, width, text, BarcodeFormat.CODE_128,
                left = 20 + index * 150, top = 20, scale = 2, barLength = 140)
        }
        val found = QrBandDecoder.decode(frame, width, height).map { it.text }
        assertTrue(found.containsAll(texts))
        assertEquals(found.size, found.distinct().size)
    }

    @Test
    fun expiredDeadlineSkipsEveryBand() {
        val frame = IntArray(width * height) { QrTestFrames.WHITE }
        QrTestFrames.horizontal(frame, width, texts[0], BarcodeFormat.CODE_128,
            left = 80, top = 15, scale = 2, barHeight = 90)
        assertTrue(QrBandDecoder.decode(frame, width, height, System.nanoTime() - 1).isEmpty())
        assertEquals(setOf(texts[0]), QrBandDecoder.decode(frame, width, height).map { it.text }.toSet())
    }
}
