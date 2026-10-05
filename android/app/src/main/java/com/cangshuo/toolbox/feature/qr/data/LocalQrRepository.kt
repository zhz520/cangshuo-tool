package com.cangshuo.toolbox.feature.qr.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.cangshuo.toolbox.feature.qr.domain.QrDecodeResult
import com.cangshuo.toolbox.feature.qr.domain.QrDecodeEntry
import com.cangshuo.toolbox.feature.qr.domain.QrCropPolicy
import com.cangshuo.toolbox.feature.qr.domain.QrCropRegion
import com.cangshuo.toolbox.feature.qr.domain.QrCodeFormat
import com.cangshuo.toolbox.feature.qr.domain.QrErrorCorrection
import com.cangshuo.toolbox.feature.qr.domain.QrFailure
import com.cangshuo.toolbox.feature.qr.domain.QrGenerateResult
import com.cangshuo.toolbox.feature.qr.domain.QrInputPolicy
import com.cangshuo.toolbox.feature.qr.domain.QrMatrix
import com.cangshuo.toolbox.feature.qr.domain.QrRepository
import com.cangshuo.toolbox.feature.qr.domain.QrPreviewResult
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.EncodeHintType
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.MultiFormatWriter
import java.io.FilterInputStream
import java.io.InputStream
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalQrRepository(context: Context) : QrRepository {
    private val resolver = context.applicationContext.contentResolver
    // Native decoding and ZXing do not stop immediately when a coroutine is cancelled.
    private val workMutex = Mutex()

    override suspend fun generate(content: String, errorCorrection: QrErrorCorrection,
        format: QrCodeFormat): QrGenerateResult =
        withContext(Dispatchers.Default) {
            workMutex.withLock {
                currentCoroutineContext().ensureActive()
                if (content.isEmpty()) return@withLock QrGenerateResult.Empty
                if (content.length > QrInputPolicy.MAX_CONTENT_BYTES ||
                    content.toByteArray(Charsets.UTF_8).size > QrInputPolicy.MAX_CONTENT_BYTES) {
                    return@withLock QrGenerateResult.Error(QrFailure.INPUT_TOO_LONG)
                }
                if (!QrInputPolicy.isUtf8(content)) return@withLock QrGenerateResult.Error(QrFailure.INVALID_TEXT)
                try {
                    val hints = buildMap<EncodeHintType, Any> {
                        put(EncodeHintType.CHARACTER_SET, "UTF-8")
                        if (format == QrCodeFormat.QR_CODE) {
                            // QR keeps the four-module quiet zone and the selected error correction.
                            put(EncodeHintType.MARGIN, 4)
                            put(EncodeHintType.ERROR_CORRECTION, when (errorCorrection) {
                                QrErrorCorrection.LOW -> ErrorCorrectionLevel.L
                                QrErrorCorrection.MEDIUM -> ErrorCorrectionLevel.M
                                QrErrorCorrection.QUARTILE -> ErrorCorrectionLevel.Q
                                QrErrorCorrection.HIGH -> ErrorCorrectionLevel.H
                            })
                        } else {
                            // Linear writers use the standard ten-module side margin.
                            put(EncodeHintType.MARGIN, 10)
                        }
                    }
                    val bits = MultiFormatWriter().encode(content, barcodeFormatOf(format), 0, 0, hints)
                    currentCoroutineContext().ensureActive()
                    val rows = List(bits.height) { y -> BooleanArray(bits.width) { x -> bits.get(x, y) } }
                    QrGenerateResult.Success(QrMatrix(bits.width, bits.height, rows), content)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: WriterException) {
                    QrGenerateResult.Error(QrFailure.CAPACITY_EXCEEDED)
                } catch (_: OutOfMemoryError) {
                    QrGenerateResult.Error(QrFailure.MEMORY_LIMIT)
                } catch (_: Exception) {
                    QrGenerateResult.Error(QrFailure.GENERATE_FAILED)
                }
            }
        }

    override suspend fun decode(uri: Uri, multi: Boolean, region: QrCropRegion?): QrDecodeResult = withContext(Dispatchers.IO) {
        workMutex.withLock {
            var bitmap: Bitmap? = null
            var cropped: Bitmap? = null
            try {
                currentCoroutineContext().ensureActive()
                val decoded = loadSampledBitmap(uri, preview = false).also { bitmap = it }
                val target = if (region == null) decoded else {
                    // The overlay uses the same policy, so the decoded square matches the visible one.
                    val rect = QrCropPolicy.rect(region, decoded.width, decoded.height)
                        ?: throw QrProcessingException(QrFailure.INVALID_IMAGE)
                    Bitmap.createBitmap(decoded, rect.left, rect.top, rect.side, rect.side).also { cropped = it }
                }
                withContext(Dispatchers.Default) { decodeBitmap(target, multi) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: QrProcessingException) {
                QrDecodeResult.Error(error.reason)
            } catch (_: OutOfMemoryError) {
                QrDecodeResult.Error(QrFailure.MEMORY_LIMIT)
            } catch (_: SecurityException) {
                QrDecodeResult.Error(QrFailure.IMAGE_READ_FAILED)
            } catch (_: Exception) {
                QrDecodeResult.Error(QrFailure.IMAGE_READ_FAILED)
            } finally {
                if (cropped !== bitmap) cropped?.recycle()
                bitmap?.recycle()
            }
        }
    }

    /** Gallery preview for the manual crop overlay; bounded to 1024 px and never recycled by callers. */
    override suspend fun loadPreview(uri: Uri): QrPreviewResult = withContext(Dispatchers.IO) {
        workMutex.withLock {
            try {
                currentCoroutineContext().ensureActive()
                QrPreviewResult.Success(loadSampledBitmap(uri, preview = true))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: QrProcessingException) {
                QrPreviewResult.Error(error.reason)
            } catch (_: OutOfMemoryError) {
                QrPreviewResult.Error(QrFailure.MEMORY_LIMIT)
            } catch (_: SecurityException) {
                QrPreviewResult.Error(QrFailure.IMAGE_READ_FAILED)
            } catch (_: Exception) {
                QrPreviewResult.Error(QrFailure.IMAGE_READ_FAILED)
            }
        }
    }

    private suspend fun loadSampledBitmap(uri: Uri, preview: Boolean): Bitmap {
        if (uri.scheme != "content") throw QrProcessingException(QrFailure.IMAGE_READ_FAILED)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use {
            BitmapFactory.decodeStream(it, null, bounds)
            it.checkBudget()
        }
        currentCoroutineContext().ensureActive()
        val heapBytes = Runtime.getRuntime().maxMemory()
        val sample = if (preview) {
            QrImagePolicy.previewSample(bounds.outWidth, bounds.outHeight, heapBytes)
        } else {
            QrImagePolicy.sample(bounds.outWidth, bounds.outHeight, heapBytes)
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        var bitmap: Bitmap? = null
        open(uri).use {
            bitmap = BitmapFactory.decodeStream(it, null, options)
            it.checkBudget()
        }
        currentCoroutineContext().ensureActive()
        val decoded = bitmap ?: throw QrProcessingException(QrFailure.INVALID_IMAGE)
        try {
            if (preview) QrImagePolicy.validatePreview(decoded.width, decoded.height, heapBytes)
            else QrImagePolicy.validateDecoded(decoded.width, decoded.height, heapBytes)
        } catch (error: Exception) {
            decoded.recycle()
            throw error
        }
        return decoded
    }

    private suspend fun decodeBitmap(bitmap: Bitmap, multi: Boolean): QrDecodeResult {
        currentCoroutineContext().ensureActive()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (index in pixels.indices) {
            if (index % 16384 == 0) currentCoroutineContext().ensureActive()
            val pixel = pixels[index]
            val alpha = pixel ushr 24
            if (alpha != 255) {
                // Transparent QR images are viewed and decoded over a white background.
                val red = ((pixel ushr 16 and 255) * alpha + 255 * (255 - alpha)) / 255
                val green = ((pixel ushr 8 and 255) * alpha + 255 * (255 - alpha)) / 255
                val blue = ((pixel and 255) * alpha + 255 * (255 - alpha)) / 255
                pixels[index] = (255 shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        if (multi) {
            val merged = LinkedHashMap<String, QrDecodeEntry>()
            decodeMultipleEntries(source).forEach { entry -> merged.putIfAbsent(entry.text, entry) }
            // Bounded extra work shared by the band passes and the dense mixed-frame pass.
            val extraDeadline = System.nanoTime() + EXTRA_PASS_BUDGET_NANOS
            if (merged.size < 2) {
                QrBandDecoder.decode(pixels, bitmap.width, bitmap.height, extraDeadline).forEach { entry ->
                    merged.putIfAbsent(entry.text, entry)
                }
                currentCoroutineContext().ensureActive()
            } else if (System.nanoTime() <= extraDeadline) {
                // Several QR codes matched; one linear-only pass still catches a barcode in the frame.
                QrLinearScanner.scanFirst(pixels, bitmap.width, bitmap.height)?.let { entry ->
                    merged.putIfAbsent(entry.text, entry)
                }
            }
            if (merged.size == 1) return QrDecodeResult.Success(merged.values.first())
            if (merged.size > 1) return QrDecodeResult.Multiple(merged.values.toList())
        }
        return decodeSingle(source)
    }

    /**
     * QR multi-code detection. ZXing's multi reader finds every QR code in one image; linear
     * barcodes are not part of this path, so callers fall back to [decodeSingle] when it finds none.
     */
    private suspend fun decodeMultipleEntries(source: RGBLuminanceSource): List<QrDecodeEntry> {
        for (inverted in listOf(false, true)) {
            currentCoroutineContext().ensureActive()
            val luminance = if (inverted) source.invert() else source
            val entries = QrMultiDecoder.decode(luminance)
            currentCoroutineContext().ensureActive()
            if (entries.isNotEmpty()) return entries
        }
        return emptyList()
    }

    private suspend fun decodeSingle(source: RGBLuminanceSource): QrDecodeResult {
        // Gallery import shares the camera symbology list, so photos of linear barcodes work too.
        val reader = QrBarcodeFormats.newReader()
        var damaged = false
        try {
            for (inverted in listOf(false, true)) {
                currentCoroutineContext().ensureActive()
                val luminance = if (inverted) source.invert() else source
                try {
                    val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(luminance)))
                    currentCoroutineContext().ensureActive()
                    return QrDecodeResult.Success(
                        QrDecodeEntry(result.text, QrBarcodeFormats.symbologyOf(result.barcodeFormat)),
                    )
                } catch (_: NotFoundException) {
                    // Try reversed luminance once for light symbols on a dark background.
                } catch (_: ChecksumException) {
                    damaged = true
                } catch (_: FormatException) {
                    damaged = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return QrDecodeResult.Error(QrFailure.DECODE_FAILED)
                } finally {
                    reader.reset()
                }
            }
        } finally {
            reader.reset()
        }
        return QrDecodeResult.Error(if (damaged) QrFailure.DAMAGED_CODE else QrFailure.NOT_FOUND)
    }

    private suspend fun open(uri: Uri): BoundedInputStream = BoundedInputStream(
        resolver.openInputStream(uri) ?: throw QrProcessingException(QrFailure.IMAGE_READ_FAILED),
        currentCoroutineContext(),
    )

    private fun barcodeFormatOf(format: QrCodeFormat): BarcodeFormat = when (format) {
        QrCodeFormat.QR_CODE -> BarcodeFormat.QR_CODE
        QrCodeFormat.CODE_128 -> BarcodeFormat.CODE_128
        QrCodeFormat.CODE_39 -> BarcodeFormat.CODE_39
        QrCodeFormat.CODE_93 -> BarcodeFormat.CODE_93
        QrCodeFormat.EAN_13 -> BarcodeFormat.EAN_13
        QrCodeFormat.EAN_8 -> BarcodeFormat.EAN_8
        QrCodeFormat.UPC_A -> BarcodeFormat.UPC_A
        QrCodeFormat.ITF -> BarcodeFormat.ITF
        QrCodeFormat.CODABAR -> BarcodeFormat.CODABAR
    }

    private companion object {
        /** Upper bound for the optional band/mixed passes of one multi-code frame. */
        const val EXTRA_PASS_BUDGET_NANOS = 1_500_000_000L
    }

    private class BoundedInputStream(input: InputStream, private val operationContext: CoroutineContext) : FilterInputStream(input) {
        private var consumed = 0L
        private var exceeded = false
        fun checkBudget() {
            operationContext.ensureActive()
            if (exceeded) throw QrProcessingException(QrFailure.IMAGE_TOO_LARGE)
        }
        private fun count(size: Long) {
            consumed += size
            if (consumed > READ_LIMIT) exceeded = true
            checkBudget()
        }
        override fun read(): Int {
            checkBudget()
            return `in`.read().also { if (it >= 0) count(1) }
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            checkBudget()
            val boundedLength = minOf(length.toLong(), READ_LIMIT - consumed + 1).toInt()
            return `in`.read(buffer, offset, boundedLength).also { if (it > 0) count(it.toLong()) }
        }
        override fun skip(size: Long): Long {
            checkBudget()
            return `in`.skip(minOf(size, READ_LIMIT - consumed + 1)).also { count(it) }
        }

        companion object { private const val READ_LIMIT = 64L * 1024 * 1024 }
    }
}
