package com.cangshuo.toolbox.feature.qr.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.RGBLuminanceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrLinearScannerTest {
    private val width = 480
    private val height = 360

    @Test
    fun denseMixedFrameKeepsBothQrCodesAndTheLinearSymbol() {
        val frame = IntArray(width * height) { QrTestFrames.WHITE }
        QrTestFrames.qr(frame, width, "MIXED-QR-1", left = 20, top = 20, scale = 4)
        QrTestFrames.qr(frame, width, "MIXED-QR-2", left = 180, top = 20, scale = 4)
        QrTestFrames.horizontal(frame, width, "MIXED-LINEAR-3", BarcodeFormat.CODE_128,
            left = 60, top = 220, scale = 2, barHeight = 90)

        val qrEntries = QrMultiDecoder.decode(RGBLuminanceSource(width, height, frame))
        assertEquals(setOf("MIXED-QR-1", "MIXED-QR-2"), qrEntries.map { it.text }.toSet())

        val linear = QrLinearScanner.scanFirst(frame, width, height)
        assertEquals("MIXED-LINEAR-3", linear?.text)
    }

    @Test
    fun qrOnlyAndBlankFramesHaveNoLinearSymbol() {
        val frame = IntArray(width * height) { QrTestFrames.WHITE }
        QrTestFrames.qr(frame, width, "QR-ONLY", left = 40, top = 40, scale = 4)
        assertNull(QrLinearScanner.scanFirst(frame, width, height))
        assertNull(QrLinearScanner.scanFirst(IntArray(width * height) { QrTestFrames.WHITE }, width, height))
        assertNull(QrLinearScanner.scanFirst(IntArray(0), width, height))
    }
}
