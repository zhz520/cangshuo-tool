package com.cangshuo.toolbox.feature.drawboard

import com.cangshuo.toolbox.feature.drawboard.domain.DrawPoint
import com.cangshuo.toolbox.feature.drawboard.domain.DrawStroke
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardPolicy
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingBoardPolicyTest {
    @Test fun pointsAreClampedAndSinglePointStrokesAreValid() {
        assertEquals(DrawPoint(0f, 0f), DrawingBoardPolicy.normalizePoint(DrawPoint(-2f, -0.1f)))
        assertEquals(DrawPoint(1f, 1f), DrawingBoardPolicy.normalizePoint(DrawPoint(2f, 1.1f)))
        assertTrue(DrawingBoardPolicy.validStroke(DrawStroke(listOf(DrawPoint(0.5f, 0.5f)), -1, 0.01f)))
    }

    @Test fun documentCountsAndWidthsAreBounded() {
        val stroke = DrawStroke(listOf(DrawPoint(0.2f, 0.2f), DrawPoint(0.8f, 0.8f)), -1, 0.01f)
        assertTrue(DrawingBoardPolicy.validDocument(DrawingDocument(List(DrawingBoardPolicy.MAX_STROKES) { stroke })))
        assertFalse(DrawingBoardPolicy.validDocument(DrawingDocument(List(DrawingBoardPolicy.MAX_STROKES + 1) { stroke })))
        assertFalse(DrawingBoardPolicy.validStroke(stroke.copy(widthFraction = 0f)))
        assertFalse(DrawingBoardPolicy.validStroke(stroke.copy(points = emptyList())))
    }

    @Test fun fillPointsWithoutSplittingAreRejected() {
        val tooMany = List(DrawingBoardPolicy.MAX_POINTS_PER_STROKE + 1) { DrawPoint(0.5f, 0.5f) }
        assertFalse(DrawingBoardPolicy.validStroke(DrawStroke(tooMany, -1, 0.01f)))
    }

    @Test fun exportedFileNameIsStablePng() {
        assertTrue(DrawingBoardPolicy.fileName().matches(Regex("drawing-\\d{8}-\\d{6}-\\d{3}\\.png")))
    }
}
