package com.cangshuo.toolbox.feature.drawboard.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DrawPoint(val x: Float, val y: Float)

data class DrawStroke(
    val points: List<DrawPoint>,
    val argb: Int,
    val widthFraction: Float,
)

data class DrawingDocument(
    val strokes: List<DrawStroke> = emptyList(),
    val clipRadiusFraction: Float = 0f,
)

enum class DrawingBoardError { EMPTY, TOO_LARGE, SAVE_FAILED, SHARE_FAILED, MEMORY_LIMIT }

class DrawingBoardException(val error: DrawingBoardError) : Exception(error.name)

interface DrawingBoardRepository {
    suspend fun save(document: DrawingDocument, destination: String?): Result<String>

    suspend fun prepareShare(document: DrawingDocument): Result<String>
}

object DrawingBoardPolicy {
    const val MAX_STROKES = 80
    const val MAX_POINTS_PER_STROKE = 1_000
    const val MAX_TOTAL_POINTS = 8_000
    const val MIN_BRUSH_DP = 1
    const val MAX_BRUSH_DP = 24
    const val EXPORT_WIDTH = 1_200
    const val EXPORT_HEIGHT = 1_600
    const val MAX_FILE_BYTES = 12L * 1024L * 1024L
    const val WHITE = 0xFFFFFFFF.toInt()
    const val MAX_UNDO = 30

    fun normalizePoint(point: DrawPoint): DrawPoint =
        DrawPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))

    fun validStroke(stroke: DrawStroke): Boolean =
        stroke.points.isNotEmpty() && stroke.points.size <= MAX_POINTS_PER_STROKE &&
            stroke.widthFraction in 0.0005f..0.12f

    fun validDocument(document: DrawingDocument): Boolean =
        document.strokes.size <= MAX_STROKES &&
            document.strokes.sumOf { it.points.size } <= MAX_TOTAL_POINTS &&
            document.strokes.all(::validStroke)

    fun fileName(): String =
        "drawing-" + SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date()) + ".png"
}
