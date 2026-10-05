package com.cangshuo.toolbox.feature.qr.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.cangshuo.toolbox.feature.qr.domain.QrExportFailure
import com.cangshuo.toolbox.feature.qr.domain.QrExportFormat
import com.cangshuo.toolbox.feature.qr.domain.QrExportLayout
import com.cangshuo.toolbox.feature.qr.domain.QrExportRepository
import com.cangshuo.toolbox.feature.qr.domain.QrExportResult
import com.cangshuo.toolbox.feature.qr.domain.QrExportSnapshot
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalQrExportRepository(context: Context) : QrExportRepository {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val directory = File(appContext.cacheDir, "qr_exports")
    private val mutex = Mutex()

    override suspend fun save(snapshot: QrExportSnapshot, destination: Uri?): QrExportResult =
        operation(QrExportFailure.SAVE_FAILED) {
            // Reject unsupported destinations before any pixel allocation.
            if (destination != null && destination.scheme != "content") fail(QrExportFailure.SAVE_FAILED)
            if (destination == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) fail(QrExportFailure.SAVE_FAILED)
            var file: File? = null
            var target: Uri? = destination
            var committed = false
            try {
                val rendered = render(snapshot)
                file = rendered
                currentCoroutineContext().ensureActive()
                if (target == null) {
                    val values = ContentValues().apply {
                        if (snapshot.fileFormat == QrExportFormat.SVG) fail(QrExportFailure.SAVE_FAILED)
                        put(MediaStore.Images.Media.DISPLAY_NAME, rendered.name)
                        put(MediaStore.Images.Media.MIME_TYPE,
                            if (snapshot.fileFormat == QrExportFormat.JPEG) "image/jpeg" else "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CangshuoToolbox")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    target = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                        ?: fail(QrExportFailure.SAVE_FAILED)
                }
                val uri = target ?: fail(QrExportFailure.SAVE_FAILED)
                rendered.inputStream().use { input ->
                    val stream = resolver.openOutputStream(uri, "wt") ?: fail(QrExportFailure.SAVE_FAILED)
                    stream.use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                }
                currentCoroutineContext().ensureActive()
                if (destination == null) {
                    val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    if (resolver.update(uri, values, null, null) != 1) fail(QrExportFailure.SAVE_FAILED)
                }
                committed = true
                QrExportResult.Success(uri)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    file?.delete()
                    if (!committed) target?.let { uri ->
                        try {
                            if (destination == null) resolver.delete(uri, null, null)
                            else DocumentsContract.deleteDocument(resolver, uri)
                        } catch (_: Exception) {
                            // A provider may refuse removal; never hide the original export failure.
                        }
                    }
                }
            }
        }

    override suspend fun prepareShare(snapshot: QrExportSnapshot): QrExportResult {
        var file: File? = null
        var delivered = false
        try {
            val result = operation(QrExportFailure.SHARE_FAILED) {
                val rendered = render(snapshot)
                file = rendered
                currentCoroutineContext().ensureActive()
                QrExportResult.Success(FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", rendered))
            }
            delivered = result is QrExportResult.Success
            return result
        } finally {
            if (!delivered) withContext(NonCancellable + Dispatchers.IO) { file?.delete() }
        }
    }

    private suspend fun operation(fallback: QrExportFailure, block: suspend () -> QrExportResult): QrExportResult = try {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                block()
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ExportException) {
        QrExportResult.Error(failure.reason)
    } catch (_: OutOfMemoryError) {
        QrExportResult.Error(QrExportFailure.MEMORY_LIMIT)
    } catch (_: Exception) {
        QrExportResult.Error(fallback)
    }

    private suspend fun render(snapshot: QrExportSnapshot): File {
        if (snapshot.fileFormat == QrExportFormat.SVG) return renderSvg(snapshot)
        val layout = QrExportLayout.forSnapshot(snapshot) ?: fail(QrExportFailure.INVALID_RESULT)
        val imageWidth = when (layout) {
            is QrExportLayout.Qr -> layout.side
            is QrExportLayout.Linear -> layout.width
        }
        val imageHeight = when (layout) {
            is QrExportLayout.Qr -> layout.side
            is QrExportLayout.Linear -> layout.height
        }
        val requiredBytes = imageWidth.toLong() * imageHeight * 4 + 4 * MIB
        if (requiredBytes > minOf(32 * MIB, Runtime.getRuntime().maxMemory() / 4)) fail(QrExportFailure.MEMORY_LIMIT)
        ensureCacheSpace()
        val file = File(directory, "qr_${UUID.randomUUID()}.${if (snapshot.fileFormat == QrExportFormat.JPEG) "jpg" else "png"}")
        var bitmap: Bitmap? = null
        var completed = false
        try {
            withContext(Dispatchers.Default) {
                val image = createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
                // Keep ownership outside withContext: cancellation can discard its returned value.
                bitmap = image
                var rendered = false
                try {
                    image.eraseColor(snapshot.colorStyle.lightColorArgb.toInt())
                    val canvas = Canvas(image)
                    val paint = Paint().apply { color = snapshot.colorStyle.darkColorArgb.toInt(); isAntiAlias = false }
                    when (layout) {
                        is QrExportLayout.Qr -> for (y in 0 until snapshot.matrix.height) {
                            currentCoroutineContext().ensureActive()
                            for (x in 0 until snapshot.matrix.width) if (snapshot.matrix.data[y][x]) {
                                val left = layout.offset + x * layout.cell
                                val top = layout.offset + y * layout.cell
                                canvas.drawRect(left.toFloat(), top.toFloat(), (left + layout.cell).toFloat(), (top + layout.cell).toFloat(), paint)
                            }
                        }
                        is QrExportLayout.Linear -> {
                            val bars = snapshot.matrix.data.first()
                            for (x in bars.indices) {
                                if (x % 512 == 0) currentCoroutineContext().ensureActive()
                                if (!bars[x]) continue
                                val left = layout.leftOffset + x * layout.barWidth
                                canvas.drawRect(left.toFloat(), layout.topOffset.toFloat(),
                                    (left + layout.barWidth).toFloat(),
                                    (layout.topOffset + layout.barHeight).toFloat(), paint)
                            }
                        }
                    }
                    rendered = true
                } finally {
                    if (!rendered) image.recycle()
                }
            }
            currentCoroutineContext().ensureActive()
            file.outputStream().buffered().use { stream ->
                val limited = LimitedOutputStream(stream)
                val image = bitmap ?: fail(QrExportFailure.INVALID_RESULT)
                val format = if (snapshot.fileFormat == QrExportFormat.JPEG)
                    Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                val quality = if (snapshot.fileFormat == QrExportFormat.JPEG) 95 else 100
                val encoded = image.compress(format, quality, limited)
                if (limited.exceeded) fail(QrExportFailure.OUTPUT_LIMIT)
                if (!encoded) fail(QrExportFailure.SAVE_FAILED)
            }
            currentCoroutineContext().ensureActive()
            completed = true
            return file
        } finally {
            bitmap?.recycle()
            if (!completed) file.delete()
        }
    }

    private suspend fun renderSvg(snapshot: QrExportSnapshot): File {
        ensureCacheSpace()
        val file = File(directory, "qr_${UUID.randomUUID()}.svg")
        var completed = false
        try {
            val text = withContext(Dispatchers.Default) {
                try {
                    QrSvgWriter.write(snapshot)
                } catch (_: QrSvgWriter.SvgLimitException) {
                    fail(QrExportFailure.OUTPUT_LIMIT)
                }
            }
            currentCoroutineContext().ensureActive()
            file.outputStream().buffered().use { stream ->
                val limited = LimitedOutputStream(stream)
                limited.write(text.toByteArray(Charsets.UTF_8))
                limited.flush()
                if (limited.exceeded) fail(QrExportFailure.OUTPUT_LIMIT)
            }
            currentCoroutineContext().ensureActive()
            completed = true
            return file
        } finally {
            if (!completed) file.delete()
        }
    }

    private fun ensureCacheSpace() {
        if (!directory.isDirectory && !directory.mkdirs()) fail(QrExportFailure.CACHE_FULL)
        val files = directory.listFiles()?.filter { it.isFile }.orEmpty()
        val cutoff = System.currentTimeMillis() - CACHE_LIFETIME_MS
        files.filter { it.lastModified() < cutoff }.forEach { it.delete() }
        if (files.filter { it.exists() }.sumOf { it.length() } + MAX_OUTPUT_BYTES > MAX_CACHE_BYTES) fail(QrExportFailure.CACHE_FULL)
    }

    private class LimitedOutputStream(output: OutputStream) : FilterOutputStream(output) {
        private var written = 0L
        var exceeded = false
            private set
        private fun reserve(count: Int) {
            if (written + count > MAX_OUTPUT_BYTES) {
                exceeded = true
                fail(QrExportFailure.OUTPUT_LIMIT)
            }
            written += count
        }
        override fun write(value: Int) { reserve(1); out.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { reserve(length); out.write(bytes, offset, length) }
    }

    private class ExportException(val reason: QrExportFailure) : Exception()

    private companion object {
        const val MIB = 1024L * 1024
        const val MAX_OUTPUT_BYTES = 4 * MIB
        const val MAX_CACHE_BYTES = 32 * MIB
        const val CACHE_LIFETIME_MS = 24 * 60 * 60 * 1000L
        fun fail(reason: QrExportFailure): Nothing = throw ExportException(reason)
    }
}
