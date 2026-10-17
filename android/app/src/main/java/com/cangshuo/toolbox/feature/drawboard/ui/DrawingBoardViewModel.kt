package com.cangshuo.toolbox.feature.drawboard.ui

import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.drawboard.domain.DrawPoint
import com.cangshuo.toolbox.feature.drawboard.domain.DrawStroke
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardError
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardException
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardPolicy
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardRepository
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DrawingBoardViewModel(
    private val repository: DrawingBoardRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var document = restoreDocument()
    private val undo = ArrayDeque<DrawingDocument>()
    private val redo = ArrayDeque<DrawingDocument>()
    private var generation = 0L
    private var activeJob: Job? = null
    private val mutable = MutableStateFlow(
        DrawingBoardUiState(
            document = document,
            selectedArgb = saved.get<Int>("drawColor") ?: Color.BLACK,
            brushSizeDp = (saved.get<Int>("drawWidth") ?: 6)
                .coerceIn(DrawingBoardPolicy.MIN_BRUSH_DP, DrawingBoardPolicy.MAX_BRUSH_DP),
            eraser = saved["drawEraser"] ?: false,
            gridMode = saved["drawGrid"] ?: false,
        ),
    )
    val state: StateFlow<DrawingBoardUiState> = mutable.asStateFlow()

    fun setColor(argb: Int) {
        if (mutable.value.busy) return
        saved["drawColor"] = argb
        mutable.value = mutable.value.copy(selectedArgb = argb, eraser = false)
        saved["drawEraser"] = false
    }

    fun setEraser(enabled: Boolean) {
        if (mutable.value.busy) return
        saved["drawEraser"] = enabled
        mutable.value = mutable.value.copy(eraser = enabled)
    }

    fun setGridMode(enabled: Boolean) {
        if (mutable.value.busy) return
        saved["drawGrid"] = enabled
        mutable.value = mutable.value.copy(gridMode = enabled)
    }

    fun setBrushSize(value: Int) {
        if (mutable.value.busy) return
        val normalized = value.coerceIn(DrawingBoardPolicy.MIN_BRUSH_DP, DrawingBoardPolicy.MAX_BRUSH_DP)
        saved["drawWidth"] = normalized
        mutable.value = mutable.value.copy(brushSizeDp = normalized)
    }

    fun addStroke(
        points: List<DrawPoint>,
        widthFraction: Float,
        argb: Int,
        clipRadiusFraction: Float,
    ): Boolean {
        if (mutable.value.busy || points.isEmpty()) return false
        val normalized = points.take(DrawingBoardPolicy.MAX_POINTS_PER_STROKE)
            .map(DrawingBoardPolicy::normalizePoint)
        val total = document.strokes.sumOf { it.points.size } + normalized.size
        if (document.strokes.size >= DrawingBoardPolicy.MAX_STROKES ||
            total > DrawingBoardPolicy.MAX_TOTAL_POINTS
        ) {
            mutable.value = mutable.value.copy(message = DrawingBoardMessage.TOO_LARGE)
            return false
        }
        val stroke = DrawStroke(normalized, argb, widthFraction.coerceIn(0.0005f, 0.12f))
        if (!DrawingBoardPolicy.validStroke(stroke)) return false
        rememberUndo()
        document = document.copy(
            strokes = document.strokes + stroke,
            clipRadiusFraction = document.clipRadiusFraction.takeIf { it > 0f }
                ?: clipRadiusFraction.coerceIn(0f, 0.2f),
        )
        redo.clear()
        publishDocument()
        return true
    }

    fun undo() {
        if (mutable.value.busy || undo.isEmpty()) return
        redo.addLast(document)
        document = undo.removeLast()
        publishDocument()
    }

    fun redo() {
        if (mutable.value.busy || redo.isEmpty()) return
        undo.addLast(document)
        document = redo.removeLast()
        publishDocument()
    }

    fun clear() {
        if (mutable.value.busy || document.strokes.isEmpty()) return
        rememberUndo()
        document = DrawingDocument()
        redo.clear()
        publishDocument()
    }

    fun save(destination: String?) {
        if (mutable.value.busy || !DrawingBoardPolicy.validDocument(document)) return
        val operation = ++generation
        mutable.value = mutable.value.copy(saving = true, message = null)
        activeJob = viewModelScope.launch {
            try {
                val result = repository.save(document, destination)
                if (generation != operation) return@launch
                mutable.value = mutable.value.copy(
                    saving = false,
                    message = result.fold(
                        { DrawingBoardMessage.SAVED },
                        { error ->
                            (error as? DrawingBoardException)?.toMessage()
                                ?: DrawingBoardMessage.SAVE_FAILED
                        },
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == operation) {
                    mutable.value = mutable.value.copy(saving = false, message = DrawingBoardMessage.SAVE_FAILED)
                }
            }
        }
    }

    fun share() {
        if (mutable.value.busy || !DrawingBoardPolicy.validDocument(document)) return
        val operation = ++generation
        mutable.value = mutable.value.copy(sharing = true, message = null)
        activeJob = viewModelScope.launch {
            try {
                val result = repository.prepareShare(document)
                if (generation != operation) return@launch
                mutable.value = result.fold(
                    { uri -> mutable.value.copy(sharing = false, shareRequest = uri) },
                    { error ->
                        val drawingError = error as? DrawingBoardException
                        mutable.value.copy(
                            sharing = false,
                            message = if (drawingError?.error == DrawingBoardError.TOO_LARGE ||
                                drawingError?.error == DrawingBoardError.MEMORY_LIMIT
                            ) DrawingBoardMessage.TOO_LARGE else DrawingBoardMessage.SHARE_FAILED,
                        )
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == operation) {
                    mutable.value = mutable.value.copy(sharing = false, message = DrawingBoardMessage.SHARE_FAILED)
                }
            }
        }
    }

    fun shareHandled(success: Boolean) {
        mutable.value = mutable.value.copy(
            shareRequest = null,
            message = if (success) mutable.value.message else DrawingBoardMessage.SHARE_FAILED,
        )
    }

    fun consumeMessage() {
        if (mutable.value.message != null) mutable.value = mutable.value.copy(message = null)
    }

    fun onLeavePage() {
        generation++
        activeJob?.cancel()
        activeJob = null
        mutable.value = mutable.value.copy(
            saving = false,
            sharing = false,
            shareRequest = null,
        )
    }

    private fun rememberUndo() {
        undo.addLast(document)
        while (undo.size > DrawingBoardPolicy.MAX_UNDO) undo.removeFirst()
    }

    private fun publishDocument() {
        saved["drawDocument"] = encode(document)
        saved["drawClip"] = document.clipRadiusFraction
        mutable.value = mutable.value.copy(
            document = document,
            canUndo = undo.isNotEmpty(),
            canRedo = redo.isNotEmpty(),
        )
    }

    private fun restoreDocument(): DrawingDocument {
        val decoded = decode(saved.get<String>("drawDocument"))
        val restored = decoded.copy(
            clipRadiusFraction = (saved.get<Float>("drawClip") ?: 0f).coerceIn(0f, 0.2f),
        )
        return restored.takeIf(DrawingBoardPolicy::validDocument) ?: DrawingDocument()
    }

    private fun DrawingBoardException.toMessage(): DrawingBoardMessage = when (error) {
        DrawingBoardError.MEMORY_LIMIT, DrawingBoardError.TOO_LARGE -> DrawingBoardMessage.TOO_LARGE
        DrawingBoardError.SHARE_FAILED -> DrawingBoardMessage.SHARE_FAILED
        else -> DrawingBoardMessage.SAVE_FAILED
    }

    companion object {
        private const val WIDTH_SCALE = 1_000_000f
        private const val POINT_SCALE = 1_000f

        fun factory(repository: DrawingBoardRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    DrawingBoardViewModel(repository, extras.createSavedStateHandle()) as T
            }

        private fun encode(document: DrawingDocument): String = buildString {
            document.strokes.forEachIndexed { index, stroke ->
                if (index > 0) append('|')
                append(stroke.argb.toUInt().toString(16))
                append(';')
                append((stroke.widthFraction * WIDTH_SCALE).roundToInt())
                append(';')
                stroke.points.forEachIndexed { pointIndex, point ->
                    if (pointIndex > 0) append(',')
                    append((point.x * POINT_SCALE).roundToInt())
                    append(':')
                    append((point.y * POINT_SCALE).roundToInt())
                }
            }
        }

        private fun decode(encoded: String?): DrawingDocument {
            if (encoded.isNullOrBlank()) return DrawingDocument()
            return try {
                val strokes = encoded.split('|').mapNotNull { value ->
                    val fields = value.split(';', limit = 3)
                    if (fields.size != 3) return@mapNotNull null
                    val argb = fields[0].toUInt(16).toInt()
                    val width = fields[1].toInt().coerceAtLeast(1) / WIDTH_SCALE
                    val points = fields[2].split(',').mapNotNull { pair ->
                        val coords = pair.split(':', limit = 2)
                        if (coords.size != 2) null else DrawPoint(
                            coords[0].toInt().coerceIn(0, POINT_SCALE.toInt()) / POINT_SCALE,
                            coords[1].toInt().coerceIn(0, POINT_SCALE.toInt()) / POINT_SCALE,
                        )
                    }
                    DrawStroke(points, argb, width)
                }
                DrawingDocument(strokes.take(DrawingBoardPolicy.MAX_STROKES))
            } catch (_: Exception) {
                DrawingDocument()
            }
        }
    }
}
