package com.cangshuo.toolbox.feature.qr.data

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.cangshuo.toolbox.feature.qr.domain.QrDecodeEntry
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decodes CameraX analysis frames with ZXing.
 *
 * CameraX delivers frames to a single analysis executor, so the luminance buffers can be reused
 * without extra locking. Failure to find a code is the normal case for most frames and is never
 * reported; the analysis use case keeps only the latest image, so a new frame replaces the old one.
 * The first successful result wins and later frames are ignored.
 *
 * PlanarYUVLuminanceSource indexes rows as `(y + top) * dataWidth + left`, so the Y plane must be
 * copied without row-stride padding before it is handed to ZXing.
 */
class QrCameraAnalyzer(
    /** Collect mode keeps scanning and reports every new payload instead of stopping at the first. */
    private val collect: Boolean,
    private val onDecoded: (QrDecodeEntry) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader: MultiFormatReader = QrBarcodeFormats.newReader()
    private val finished = AtomicBoolean(false)
    private val collector = QrCameraCollector()
    private var luminance = ByteArray(0)
    private var rotated = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        try {
            if (finished.get()) return
            val entry = decode(image) ?: return
            if (entry.text.isEmpty()) return
            if (collect) {
                if (collector.add(entry.text)) onDecoded(entry)
            } else if (finished.compareAndSet(false, true)) {
                onDecoded(entry)
            }
        } catch (_: Exception) {
            // A single bad frame must not stop the preview; the next frame is analysed instead.
        } finally {
            image.close()
        }
    }

    private fun decode(image: ImageProxy): QrDecodeEntry? {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null
        val count = width.toLong() * height
        if (count > QrFrameLuminance.MAX_FRAME_PIXELS) return null
        val size = count.toInt()
        if (luminance.size < size) luminance = ByteArray(size)
        if (rotated.size < size) rotated = ByteArray(size)
        val plane = image.planes.firstOrNull() ?: return null
        if (!QrFrameLuminance.copyPlanarY(plane.buffer, plane.rowStride, width, height, luminance)) return null
        val frame = QrFrameLuminance.orient(luminance, width, height, image.imageInfo.rotationDegrees, rotated) ?: return null
        val source = PlanarYUVLuminanceSource(frame.data, frame.width, frame.height, 0, 0, frame.width, frame.height, false)
        val result: Result = try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        } catch (_: Exception) {
            return null
        } finally {
            reader.reset()
        }
        return QrDecodeEntry(result.text, QrBarcodeFormats.symbologyOf(result.barcodeFormat))
    }
}
