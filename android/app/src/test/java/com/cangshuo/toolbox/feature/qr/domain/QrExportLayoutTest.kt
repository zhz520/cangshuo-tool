package com.cangshuo.toolbox.feature.qr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrExportLayoutTest {
    private fun linearMatrix(modules: Int) = QrMatrix(modules, 1, listOf(BooleanArray(modules) { it % 2 == 0 }))

    @Test
    fun linearLayoutCentersBarsAndKeepsQuietZones() {
        val layout = QrExportLayout.forSnapshot(QrExportSnapshot(
            matrix = linearMatrix(200), colorStyle = QrColorStyle.BLACK_WHITE,
            size = QrExportSize.MEDIUM, format = QrCodeFormat.CODE_128,
        )) as QrExportLayout.Linear
        assertEquals(1024, layout.width)
        assertEquals(341, layout.height)
        assertEquals(5, layout.barWidth)
        assertEquals((1024 - 5 * 200) / 2, layout.leftOffset)
        assertEquals(341 / 8, layout.topOffset)
        assertEquals(341 - 2 * (341 / 8), layout.barHeight)
    }

    @Test
    fun linearLayoutRejectsNonSingleRowAndTooManyModules() {
        val tall = QrMatrix(40, 3, List(3) { BooleanArray(40) })
        assertNull(QrExportLayout.forSnapshot(QrExportSnapshot(
            tall, QrColorStyle.BLACK_WHITE, QrExportSize.SMALL, QrCodeFormat.CODE_128)))
        assertNull(QrExportLayout.forSnapshot(QrExportSnapshot(
            linearMatrix(5000), QrColorStyle.BLACK_WHITE, QrExportSize.SMALL, QrCodeFormat.ITF)))
    }

    @Test
    fun qrLayoutStillUsesTheModuleGrid() {
        // Version 1: 21 modules plus the four-module quiet zone on each side.
        val matrix = QrMatrix(29, 29, List(29) { BooleanArray(29) })
        val layout = QrExportLayout.forSnapshot(QrExportSnapshot(
            matrix, QrColorStyle.BLACK_WHITE, QrExportSize.SMALL,
        )) as QrExportLayout.Qr
        assertEquals(512, layout.side)
        assertEquals(512 / 29, layout.cell)
        assertEquals((512 - 17 * 29) / 2, layout.offset)
    }
}
