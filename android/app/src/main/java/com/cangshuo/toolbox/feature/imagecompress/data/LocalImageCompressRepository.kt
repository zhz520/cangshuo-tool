package com.cangshuo.toolbox.feature.imagecompress.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.cangshuo.toolbox.feature.imagecompress.domain.CompressParams
import com.cangshuo.toolbox.feature.imagecompress.domain.CompressResult
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageCompressRepository
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageFailure
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageMetadata
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageProcessingException
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageProcessingLimits
import com.cangshuo.toolbox.feature.imagecompress.domain.OutputImageFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/** Application-scoped file ownership and serialized native bitmap work. */
class LocalImageCompressRepository(private val context: Context) : ImageCompressRepository {
    private val imageMutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeOutputs = ConcurrentHashMap.newKeySet<String>()
    private val outputDirectory = File(context.cacheDir, "image_compress")
    private val shareDirectory = File(context.cacheDir, "share_images")
    private val outputIdPattern = Regex("[0-9a-f-]{36}\\.(jpg|png|webp)")

    override suspend fun readMetadata(uri: Uri): Result<Pair<ImageMetadata, Bitmap>> = operation {
        val source = readSource(uri)
        val sample = ImageProcessingLimits.boundedPreviewSample(source.rawWidth, source.rawHeight, Runtime.getRuntime().maxMemory())
        source.metadata to decodePreview(uri, sample, source.orientation)
    }

    override suspend fun compress(uri: Uri, params: CompressParams): Result<CompressResult> {
        var output: File? = null
        var delivered = false
        try {
            val result = operation {
                val source = readSource(uri)
                val (width, height) = ImageProcessingLimits.targetSize(source.metadata.width, source.metadata.height, params)
                val decodeWidth = if (source.swapsAxes) height else width
                val decodeHeight = if (source.swapsAxes) width else height
                val sample = ImageProcessingLimits.targetSample(source.rawWidth, source.rawHeight, decodeWidth, decodeHeight)
                ImageProcessingLimits.validateMemory(source.rawWidth, source.rawHeight, sample, width, height, Runtime.getRuntime().maxMemory())
                ensureCacheSpace()
                val (format, mime) = resolveFormat(params.outputFormat, source.metadata.mimeType, params.quality)
                val file = File(outputDirectory, "${UUID.randomUUID()}${extension(mime)}")
                output = file
                var working = decode(uri, sample)
                try {
                    currentCoroutineContext().ensureActive()
                    working = orient(working, source.orientation)
                    currentCoroutineContext().ensureActive()
                    if (working.width != width || working.height != height) {
                        working = replace(working, working.scale(width, height, filter = true))
                    }
                    if (mime == "image/jpeg" && working.hasAlpha()) {
                        val opaque = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        Canvas(opaque).apply { drawColor(Color.WHITE); drawBitmap(working, 0f, 0f, null) }
                        working = replace(working, opaque)
                    }
                    currentCoroutineContext().ensureActive()
                    file.outputStream().buffered().use { stream ->
                        if (!working.compress(format, params.quality.coerceIn(1, 100), LimitedOutputStream(stream))) {
                            throw ImageProcessingException(ImageFailure.COMPRESS_FAILED)
                        }
                    }
                } finally {
                    working.recycle()
                }
                currentCoroutineContext().ensureActive()
                val outputSize = file.length()
                if (outputSize <= 0L) throw ImageProcessingException(ImageFailure.COMPRESS_FAILED)
                // Every preview, including small images, comes from the encoded output.
                val previewOptions = BitmapFactory.Options().apply {
                    inSampleSize = ImageProcessingLimits.boundedPreviewSample(width, height, Runtime.getRuntime().maxMemory())
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val preview = BitmapFactory.decodeFile(file.absolutePath, previewOptions)
                    ?: throw ImageProcessingException(ImageFailure.COMPRESS_FAILED)
                val originalSize = source.metadata.sizeBytes
                CompressResult(
                    previewBitmap = preview,
                    outputId = file.name,
                    originalMetadata = source.metadata,
                    compressedMetadata = ImageMetadata(width, height, outputSize, mime, exportName(source.metadata.fileName, mime)),
                    compressionRatio = if (originalSize > 0) ((outputSize - originalSize).toDouble() / originalSize * 100).toFloat() else null,
                    savedBytes = if (originalSize > 0) max(0L, originalSize - outputSize) else 0L,
                )
            }
            if (result.isSuccess) { activeOutputs.add(result.getOrThrow().outputId); delivered = true }
            return result
        } finally {
            if (!delivered) withContext(NonCancellable + Dispatchers.IO) { output?.delete() }
        }
    }

    override suspend fun save(result: CompressResult, destination: Uri?): Result<Uri> = operation {
        val resolver = context.contentResolver
        val sourceFile = outputFile(result.outputId)
        val isDocument = destination != null
        val target = destination ?: run {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw ImageProcessingException(ImageFailure.SAVE_FAILED)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, result.compressedMetadata.fileName)
                put(MediaStore.Images.Media.MIME_TYPE, result.compressedMetadata.mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CangshuoToolbox")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: throw ImageProcessingException(ImageFailure.SAVE_FAILED)
        }
        var committed = false
        try {
            sourceFile.inputStream().use { input ->
                val stream = resolver.openOutputStream(target, "w") ?: throw ImageProcessingException(ImageFailure.SAVE_FAILED)
                stream.use { copy(input, it) }
            }
            currentCoroutineContext().ensureActive()
            if (!isDocument) {
                val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                if (resolver.update(target, values, null, null) != 1) throw ImageProcessingException(ImageFailure.SAVE_FAILED)
            }
            committed = true
            target
        } finally {
            if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                try {
                    if (isDocument) DocumentsContract.deleteDocument(resolver, target) else resolver.delete(target, null, null)
                } catch (_: Exception) {
                    // Some document providers cannot delete an incomplete document.
                }
            }
        }
    }

    override suspend fun prepareShare(result: CompressResult): Result<Uri> {
        var sharedFile: File? = null
        var delivered = false
        try {
            val prepared = operation {
                ensureCacheSpace()
                val file = File(shareDirectory, "${UUID.randomUUID()}${extension(result.compressedMetadata.mimeType)}")
                sharedFile = file
                outputFile(result.outputId).inputStream().use { input -> file.outputStream().buffered().use { copy(input, it) } }
                currentCoroutineContext().ensureActive()
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            delivered = prepared.isSuccess
            return prepared
        } finally {
            if (!delivered) withContext(NonCancellable + Dispatchers.IO) { sharedFile?.delete() }
        }
    }

    override fun discard(result: CompressResult) {
        cleanupScope.launch {
            try {
                imageMutex.withLock {
                    activeOutputs.remove(result.outputId)
                    if (outputIdPattern.matches(result.outputId)) File(outputDirectory, result.outputId).delete()
                }
            } catch (_: Exception) {
                // Best-effort cleanup; later expiry and cache limits still apply.
            }
        }
    }

    private suspend fun <T> operation(block: suspend () -> T): Result<T> = try {
        withContext(Dispatchers.IO) { imageMutex.withLock { currentCoroutineContext().ensureActive(); Result.success(block()) } }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: OutOfMemoryError) {
        Result.failure(ImageProcessingException(ImageFailure.MEMORY_LIMIT))
    } catch (error: Exception) {
        Result.failure(error)
    }

    private data class Source(val metadata: ImageMetadata, val rawWidth: Int, val rawHeight: Int, val orientation: Int) {
        val swapsAxes: Boolean get() = orientation in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270
    }

    private fun readSource(uri: Uri): Source {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Bounds decoding returns null by design; only the stream itself must be non-null.
        val input = resolver.openInputStream(uri) ?: throw ImageProcessingException(ImageFailure.LOAD_FAILED)
        input.use { BitmapFactory.decodeStream(it, null, options) }
        ImageProcessingLimits.validateSource(options.outWidth, options.outHeight)
        val mime = options.outMimeType ?: throw ImageProcessingException(ImageFailure.INVALID_IMAGE)
        if (mime !in setOf("image/jpeg", "image/png", "image/webp")) throw ImageProcessingException(ImageFailure.UNSUPPORTED_FORMAT)
        var name = "image"
        var size = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex).take(256)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).coerceAtLeast(0L)
            }
        }
        val orientation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val swap = orientation in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270
        return Source(ImageMetadata(if (swap) options.outHeight else options.outWidth, if (swap) options.outWidth else options.outHeight, size, mime, name), options.outWidth, options.outHeight, orientation)
    }

    private fun decode(uri: Uri, sample: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw ImageProcessingException(ImageFailure.INVALID_IMAGE)
    }

    private fun decodePreview(uri: Uri, sample: Int, orientation: Int): Bitmap {
        var bitmap = decode(uri, sample)
        try { bitmap = orient(bitmap, orientation); return bitmap } catch (error: Throwable) { bitmap.recycle(); throw error }
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
            else -> return bitmap
        }
        return replace(bitmap, Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true))
    }

    private fun replace(old: Bitmap, replacement: Bitmap): Bitmap { if (old !== replacement) old.recycle(); return replacement }

    private fun outputFile(id: String): File {
        if (!outputIdPattern.matches(id)) throw ImageProcessingException(ImageFailure.INVALID_IMAGE)
        return File(outputDirectory, id).takeIf { it.isFile } ?: throw ImageProcessingException(ImageFailure.SAVE_FAILED)
    }

    private fun ensureCacheSpace() {
        val directories = listOf(outputDirectory, shareDirectory)
        directories.forEach { if (!it.isDirectory && !it.mkdirs()) throw ImageProcessingException(ImageFailure.CACHE_FULL) }
        val cutoff = System.currentTimeMillis() - ImageProcessingLimits.CACHE_LIFETIME_MS
        val files = directories.flatMap { it.listFiles()?.toList().orEmpty() }.filter { it.isFile }
        files.filter { it.lastModified() < cutoff && it.name !in activeOutputs }.forEach { it.delete() }
        val used = files.filter { it.exists() }.sumOf { it.length() }
        if (used + ImageProcessingLimits.MAX_OUTPUT_BYTES > ImageProcessingLimits.MAX_CACHE_BYTES) throw ImageProcessingException(ImageFailure.CACHE_FULL)
    }

    private suspend fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(32 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        output.flush()
    }

    private class LimitedOutputStream(output: OutputStream) : FilterOutputStream(output) {
        private var count = 0L
        private fun reserve(bytes: Int) {
            if (count + bytes > ImageProcessingLimits.MAX_OUTPUT_BYTES) throw ImageProcessingException(ImageFailure.OUTPUT_TOO_LARGE)
            count += bytes
        }
        override fun write(value: Int) { reserve(1); out.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { reserve(length); out.write(bytes, offset, length) }
    }

    private fun exportName(sourceName: String, mime: String): String {
        val prefix = sourceName.take(80)
        val base = prefix.substringBeforeLast('.', prefix)
            .map { if (it.isLetterOrDigit() || it in " _-") it else '_' }
            .joinToString("").ifBlank { "image" }
        return "${base}_compressed${extension(mime)}"
    }

    private fun extension(mime: String): String = when (mime) { "image/png" -> ".png"; "image/webp" -> ".webp"; else -> ".jpg" }

    private fun resolveFormat(format: OutputImageFormat, source: String, quality: Int): Pair<Bitmap.CompressFormat, String> {
        val selected = if (format == OutputImageFormat.MATCH_SOURCE) when (source) {
            "image/png" -> OutputImageFormat.PNG
            "image/webp" -> OutputImageFormat.WEBP
            else -> OutputImageFormat.JPEG
        } else format
        return when (selected) {
            OutputImageFormat.PNG -> Bitmap.CompressFormat.PNG to "image/png"
            OutputImageFormat.WEBP -> {
                val webp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                webp to "image/webp"
            }
            else -> Bitmap.CompressFormat.JPEG to "image/jpeg"
        }
    }
}
