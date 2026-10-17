package com.cangshuo.toolbox.feature.imagebase64.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Error
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Exception
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Limits
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Repository
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Result
import com.cangshuo.toolbox.feature.imagebase64.domain.detectImageMime
import com.cangshuo.toolbox.feature.imagebase64.domain.encodeImageBase64
import com.cangshuo.toolbox.feature.imagebase64.domain.imageBase64PreviewSampleSize
import com.cangshuo.toolbox.feature.imagebase64.domain.parseImageBase64Input
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

class LocalImageBase64Repository(private val context: Context) : ImageBase64Repository {
    private val nativeWork = Mutex()

    override suspend fun encode(uri: String): Result<ImageBase64Result> = operation {
        if (uri.toUri().scheme != "content") throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
        val activeContext = currentCoroutineContext()
        val bytes = readBytes(uri.toUri())
        buildResult(bytes, displayName(uri.toUri())) { activeContext.ensureActive() }
    }

    override suspend fun decode(text: String): Result<ImageBase64Result> = operation {
        val activeContext = currentCoroutineContext()
        val parsed = parseImageBase64Input(text) { activeContext.ensureActive() }
        buildResult(parsed.bytes, "decoded." + com.cangshuo.toolbox.feature.imagebase64.domain.imageExtension(parsed.detectedMime)) {
            activeContext.ensureActive()
        }
    }

    override suspend fun saveText(uri: String, text: String): Result<Unit> = operation {
        val target = uri.toUri()
        if (target.scheme != "content") throw ImageBase64Exception(ImageBase64Error.SAVE_FAILED)
        val stream = context.contentResolver.openOutputStream(target, "wt")
            ?: throw ImageBase64Exception(ImageBase64Error.SAVE_FAILED)
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            var offset = 0
            while (offset < text.length) {
                currentCoroutineContext().ensureActive()
                val end = (offset + 64 * 1024).coerceAtMost(text.length)
                writer.write(text, offset, end - offset)
                offset = end
            }
        }
    }

    override suspend fun prepareShareText(text: String): Result<Uri> = operation {
        val directory = File(context.cacheDir, "image_base64_share").apply { mkdirs() }
        directory.listFiles()?.forEach { file -> if (System.currentTimeMillis() - file.lastModified() > 24L * 60 * 60 * 1000) file.delete() }
        val file = File.createTempFile("image-base64-", ".txt", directory)
        var delivered = false
        try {
            file.bufferedWriter(Charsets.UTF_8).use { writer ->
                var offset = 0
                while (offset < text.length) {
                    currentCoroutineContext().ensureActive()
                    val end = (offset + 64 * 1024).coerceAtMost(text.length)
                    writer.write(text, offset, end - offset)
                    offset = end
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            delivered = true
            uri
        } finally {
            if (!delivered) file.delete()
        }
    }

    private suspend fun readBytes(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ImageBase64Exception(ImageBase64Error.LOAD_FAILED)
        return input.use { source ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = source.read(buffer)
                if (count < 0) break
                total += count
                if (total > ImageBase64Limits.MAX_BINARY_BYTES) throw ImageBase64Exception(ImageBase64Error.TOO_LARGE)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun buildResult(bytes: ByteArray, name: String, checkActive: () -> Unit): ImageBase64Result {
        checkActive()
        val mime = detectImageMime(bytes) ?: throw ImageBase64Exception(ImageBase64Error.UNSUPPORTED_FORMAT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
        checkActive()
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val transformed = orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED
        val rawBase64 = encodeImageBase64(bytes)
        checkActive()
        val reserved = rawBase64.length.toLong() * 2 + bytes.size
        val sample = imageBase64PreviewSampleSize(
            bounds.outWidth, bounds.outHeight, Runtime.getRuntime().maxMemory(), transformed, reserved,
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        var preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw ImageBase64Exception(ImageBase64Error.INVALID_INPUT)
        checkActive()
        preview = orient(preview, orientation)
        checkActive()
        val swap = orientation in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270
        return ImageBase64Result(
            preview = preview,
            mimeType = mime,
            width = preview.width,
            height = preview.height,
            sourceWidth = if (swap) bounds.outHeight else bounds.outWidth,
            sourceHeight = if (swap) bounds.outWidth else bounds.outHeight,
            byteCount = bytes.size.toLong(),
            rawBase64 = rawBase64,
            fileName = name.take(128),
        )
    }

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
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
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun displayName(uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0).take(128) else ""
        } ?: ""
    } catch (_: Exception) { "" }

    private suspend fun <T> operation(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(Dispatchers.IO) {
            nativeWork.withLock {
                currentCoroutineContext().ensureActive()
                block()
            }
        })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: ImageBase64Exception) {
        Result.failure(error)
    } catch (_: OutOfMemoryError) {
        Result.failure(ImageBase64Exception(ImageBase64Error.MEMORY_LIMIT))
    } catch (_: Exception) {
        Result.failure(ImageBase64Exception(ImageBase64Error.LOAD_FAILED))
    }
}
