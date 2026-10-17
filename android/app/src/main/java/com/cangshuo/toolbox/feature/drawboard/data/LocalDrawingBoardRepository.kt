package com.cangshuo.toolbox.feature.drawboard.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.cangshuo.toolbox.feature.drawboard.domain.DrawStroke
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardError
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardException
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardPolicy
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardRepository
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream

class LocalDrawingBoardRepository(private val context: Context) : DrawingBoardRepository {
    private val nativeWork = Mutex()
    private val shareDirectory = File(context.cacheDir, "drawing_board_share")

    init {
        shareDirectory.mkdirs()
        pruneShareCache()
    }

    override suspend fun save(document: DrawingDocument, destination: String?): Result<String> = operation {
        checkDocument(document)
        val rendered = renderTemp(document)
        try {
            val resolver = context.contentResolver
            val isDocument = destination != null
        val target = destination?.toUri() ?: run {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw DrawingBoardException(DrawingBoardError.SAVE_FAILED)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, DrawingBoardPolicy.fileName())
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CangshuoToolbox")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                    ?: throw DrawingBoardException(DrawingBoardError.SAVE_FAILED)
            }
            var committed = false
            try {
                resolver.openOutputStream(target, "wt")?.use { output ->
                    rendered.inputStream().buffered().use { input -> input.copyTo(output) }
                } ?: throw DrawingBoardException(DrawingBoardError.SAVE_FAILED)
                if (!isDocument) {
                    val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    if (resolver.update(target, values, null, null) != 1) {
                        throw DrawingBoardException(DrawingBoardError.SAVE_FAILED)
                    }
                }
                committed = true
                target.toString()
            } finally {
                if (!committed && !isDocument) withContext(NonCancellable + Dispatchers.IO) {
                    try { resolver.delete(target, null, null) } catch (_: Exception) { }
                }
            }
        } finally {
            rendered.delete()
        }
    }

    override suspend fun prepareShare(document: DrawingDocument): Result<String> = operation {
        checkDocument(document)
        shareDirectory.mkdirs()
        pruneShareCache()
        val rendered = renderTemp(document)
        val file = File(shareDirectory, DrawingBoardPolicy.fileName())
        var delivered = false
        try {
            file.outputStream().buffered().use { output ->
                rendered.inputStream().buffered().use { input -> input.copyTo(output) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            delivered = true
            uri.toString()
        } finally {
            rendered.delete()
            if (!delivered) file.delete()
        }
    }

    private fun checkDocument(document: DrawingDocument) {
        if (!DrawingBoardPolicy.validDocument(document)) throw DrawingBoardException(DrawingBoardError.TOO_LARGE)
    }

    private suspend fun renderTemp(document: DrawingDocument): File {
        val temp = File.createTempFile("drawing-", ".png", context.cacheDir)
        try {
            temp.outputStream().buffered().use { writePng(document, it) }
            return temp
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private suspend fun writePng(document: DrawingDocument, output: OutputStream) {
        val bitmap = createBitmap(DrawingBoardPolicy.EXPORT_WIDTH, DrawingBoardPolicy.EXPORT_HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply {
                drawColor(DrawingBoardPolicy.WHITE)
                if (document.clipRadiusFraction > 0f) {
                    val radius = document.clipRadiusFraction.coerceIn(0f, 0.2f) * DrawingBoardPolicy.EXPORT_WIDTH
                    clipPath(Path().apply {
                        addRoundRect(
                            RectF(
                                0f,
                                0f,
                                DrawingBoardPolicy.EXPORT_WIDTH.toFloat(),
                                DrawingBoardPolicy.EXPORT_HEIGHT.toFloat(),
                            ),
                            radius,
                            radius,
                            Path.Direction.CW,
                        )
                    })
                }
                document.strokes.forEach { drawStroke(this, it) }
            }
            currentCoroutineContext().ensureActive()
            val limited = LimitedOutputStream(output)
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, limited)) {
                throw DrawingBoardException(DrawingBoardError.SAVE_FAILED)
            }
            currentCoroutineContext().ensureActive()
        } finally {
            bitmap.recycle()
        }
    }

    private fun pruneShareCache() {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        shareDirectory.listFiles()?.forEach { file -> if (file.lastModified() < cutoff) file.delete() }
    }

    private fun drawStroke(canvas: Canvas, stroke: DrawStroke) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.argb
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = stroke.widthFraction * DrawingBoardPolicy.EXPORT_WIDTH
        }
        val points = stroke.points.map {
            androidx.compose.ui.geometry.Offset(
                it.x * DrawingBoardPolicy.EXPORT_WIDTH,
                it.y * DrawingBoardPolicy.EXPORT_HEIGHT,
            )
        }
        if (points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(points[0].x, points[0].y, paint.strokeWidth / 2f, paint)
            return
        }
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (index in 1 until points.size) {
                val previous = points[index - 1]
                val current = points[index]
                val middleX = (previous.x + current.x) / 2f
                val middleY = (previous.y + current.y) / 2f
                if (index == 1) lineTo(middleX, middleY) else quadTo(previous.x, previous.y, middleX, middleY)
            }
            val last = points.last()
            lineTo(last.x, last.y)
        }
        canvas.drawPath(path, paint)
    }

    private suspend fun <T> operation(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(Dispatchers.IO) {
            nativeWork.withLock { currentCoroutineContext().ensureActive(); block() }
        })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: DrawingBoardException) {
        Result.failure(error)
    } catch (_: OutOfMemoryError) {
        Result.failure(DrawingBoardException(DrawingBoardError.MEMORY_LIMIT))
    } catch (_: Exception) {
        Result.failure(DrawingBoardException(DrawingBoardError.SAVE_FAILED))
    }

    private class LimitedOutputStream(output: OutputStream) : FilterOutputStream(output) {
        private var count = 0L
        override fun write(value: Int) {
            if (++count > DrawingBoardPolicy.MAX_FILE_BYTES) throw DrawingBoardException(DrawingBoardError.TOO_LARGE)
            out.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (count + length > DrawingBoardPolicy.MAX_FILE_BYTES) {
                throw DrawingBoardException(DrawingBoardError.TOO_LARGE)
            }
            count += length
            out.write(bytes, offset, length)
        }
    }
}
