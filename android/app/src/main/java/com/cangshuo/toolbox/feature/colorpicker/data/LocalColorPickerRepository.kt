package com.cangshuo.toolbox.feature.colorpicker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.core.net.toUri
import androidx.core.graphics.get
import android.provider.OpenableColumns
import com.cangshuo.toolbox.feature.colorpicker.domain.ColorPickerError
import com.cangshuo.toolbox.feature.colorpicker.domain.ColorPickerException
import com.cangshuo.toolbox.feature.colorpicker.domain.ColorPickerLimits
import com.cangshuo.toolbox.feature.colorpicker.domain.ColorPickerRepository
import com.cangshuo.toolbox.feature.colorpicker.domain.LoadedImage
import com.cangshuo.toolbox.feature.colorpicker.domain.previewSampleSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Bounds-checked ARGB decode so sampling never allocates a full-resolution bitmap. */
class LocalColorPickerRepository(private val context: Context) : ColorPickerRepository {
    private val decoding = Mutex()

    override suspend fun load(uri: String): Result<LoadedImage> {
        var allocated: Bitmap? = null
        var delivered = false
        try {
            val loaded = withContext(Dispatchers.IO) {
                decoding.withLock {
                    currentCoroutineContext().ensureActive()
                    open(uri.toUri()).also { allocated = it.bitmap }
                }
            }
            delivered = true
            return Result.success(loaded)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ColorPickerException) {
            return Result.failure(error)
        } catch (_: OutOfMemoryError) {
            return Result.failure(ColorPickerException(ColorPickerError.MEMORY_LIMIT))
        } catch (_: Exception) {
            return Result.failure(ColorPickerException(ColorPickerError.LOAD_FAILED))
        } finally {
            if (!delivered) allocated?.recycle()
        }
    }

    override fun sample(image: LoadedImage, x: Int, y: Int): Int =
        image.bitmap[x.coerceIn(0, image.width - 1), y.coerceIn(0, image.height - 1)]

    private suspend fun open(uri: Uri): LoadedImage {
        if (uri.scheme != "content") throw ColorPickerException(ColorPickerError.INVALID_IMAGE)
        val directory = File(context.cacheDir, "color_picker").apply { mkdirs() }
        val snapshot = File.createTempFile("input-", ".image", directory)
        var working: Bitmap? = null
        var transferred = false
        try {
            // One bounded snapshot avoids different provider bytes on the bounds/EXIF/decode passes.
            val input = context.contentResolver.openInputStream(uri)
                ?: throw ColorPickerException(ColorPickerError.LOAD_FAILED)
            input.use { source ->
                snapshot.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > ColorPickerLimits.MAX_INPUT_BYTES) throw ColorPickerException(ColorPickerError.TOO_LARGE)
                        output.write(buffer, 0, count)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Null is expected in bounds-only mode; validate dimensions, not the returned bitmap.
            BitmapFactory.decodeFile(snapshot.absolutePath, bounds)
            val rawWidth = bounds.outWidth
            val rawHeight = bounds.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) throw ColorPickerException(ColorPickerError.INVALID_IMAGE)
            if (bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp")) {
                throw ColorPickerException(ColorPickerError.UNSUPPORTED_FORMAT)
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = previewSampleSize(rawWidth, rawHeight, heapBytes = Runtime.getRuntime().maxMemory())
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            }
            val orientation = try {
                ExifInterface(snapshot.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
            currentCoroutineContext().ensureActive()
            val decoded = BitmapFactory.decodeFile(snapshot.absolutePath, options)
                ?: throw ColorPickerException(ColorPickerError.INVALID_IMAGE)
            working = decoded
            currentCoroutineContext().ensureActive()
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            }
            val oriented = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, false)
            working = oriented
            if (oriented !== decoded) decoded.recycle()
            currentCoroutineContext().ensureActive()
            val swap = orientation in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270
            val loaded = LoadedImage(oriented, oriented.width, oriented.height,
                if (swap) rawHeight else rawWidth, if (swap) rawWidth else rawHeight, displayName(uri))
            transferred = true
            return loaded
        } finally {
            snapshot.delete()
            if (!transferred) working?.recycle()
        }
    }

    private fun displayName(uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0).take(128) else ""
        } ?: ""
    } catch (_: Exception) {
        ""
    }
}
