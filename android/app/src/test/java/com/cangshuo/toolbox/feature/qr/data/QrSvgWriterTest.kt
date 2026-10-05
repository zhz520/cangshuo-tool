package com.cangshuo.toolbox.feature.qr.data

import com.cangshuo.toolbox.feature.qr.domain.QrCodeFormat
import com.cangshuo.toolbox.feature.qr.domain.QrColorStyle
import com.cangshuo.toolbox.feature.qr.domain.QrExportFormat
import com.cangshuo.toolbox.feature.qr.domain.QrExportSize
import com.cangshuo.toolbox.feature.qr.domain.QrExportSnapshot
import com.cangshuo.toolbox.feature.qr.domain.QrMatrix
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QrSvgWriterTest {
    private fun snapshot(matrix: QrMatrix, format: QrCodeFormat) = QrExportSnapshot(
        matrix, QrColorStyle.BLACK_WHITE, QrExportSize.SMALL, format, QrExportFormat.SVG,
    )

    @Test
    fun qrSvgUsesTheModuleGridAndBothColors() {
        val matrix = QrMatrix(29, 29, List(29) { y -> BooleanArray(29) { x -> x == y } })
        val svg = QrSvgWriter.write(snapshot(matrix, QrCodeFormat.QR_CODE))
        assertTrue(svg.startsWith("<?xml"))
        assertTrue(svg.contains("viewBox=\"0 0 29 29\""))
        assertTrue(svg.contains("width=\"512\" height=\"512\""))
        assertFalse(svg.contains("preserveAspectRatio"))
        assertTrue(svg.contains("fill=\"#FFFFFF\""))
        assertTrue(svg.contains("fill=\"#000000\""))
        assertTrue(svg.contains("d=\"M0 0h1v1h-1zM1 1h1v1h-1z"))
    }

    @Test
    fun linearSvgStretchesTheSingleRow() {
        val matrix = QrMatrix(4, 1, listOf(booleanArrayOf(true, false, true, true)))
        val svg = QrSvgWriter.write(snapshot(matrix, QrCodeFormat.CODE_128))
        assertTrue(svg.contains("viewBox=\"0 0 4 1\""))
        assertTrue(svg.contains("preserveAspectRatio=\"none\""))
        assertTrue(svg.contains("width=\"512\" height=\"170\""))
        assertTrue(svg.contains("d=\"M0 0h1v1h-1zM2 0h1v1h-1zM3 0h1v1h-1z\""))
    }

    @Test
    fun oversizedPathIsRejected() {
        val dark = QrMatrix(300, 300, List(300) { BooleanArray(300) { true } })
        try {
            QrSvgWriter.write(snapshot(dark, QrCodeFormat.QR_CODE))
            fail("Expected SvgLimitException")
        } catch (_: QrSvgWriter.SvgLimitException) {
            // Expected: the caller maps this to the fixed output-limit message.
        }
    }
}
