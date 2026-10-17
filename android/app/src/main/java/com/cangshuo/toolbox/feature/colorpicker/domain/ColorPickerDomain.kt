package com.cangshuo.toolbox.feature.colorpicker.domain

import android.graphics.Bitmap
import com.cangshuo.toolbox.feature.color.data.LocalColorRepository
import com.cangshuo.toolbox.feature.color.domain.RgbColor
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

enum class ColorPickerError { INVALID_IMAGE, UNSUPPORTED_FORMAT, TOO_LARGE, MEMORY_LIMIT, LOAD_FAILED, PICKER_UNAVAILABLE }

class ColorPickerException(val reason: ColorPickerError) : Exception(reason.name)

object ColorPickerLimits {
    const val MAX_PREVIEW_EDGE = 2_048
    const val MAX_SOURCE_EDGE = 100_000
    const val MAX_SOURCE_PIXELS = 500_000_000L
    const val MAX_INPUT_BYTES = 32L * 1024 * 1024
    const val MAX_DECODE_BYTES = 64L * 1024 * 1024
    const val MAX_PALETTE = 24
}

/** A decoded image plus the source dimensions it was sampled from. */
data class LoadedImage(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val fileName: String = "",
) {
    val scaled: Boolean get() = width != sourceWidth || height != sourceHeight
}

/** One ARGB colour sampled from an image. */
data class PickedColor(val argb: Int)

/** Exact position of a sample inside the decoded bitmap. */
data class SamplePoint(val x: Int, val y: Int, val fractionX: Float, val fractionY: Float)

/** Placement of an image drawn with ContentScale.Fit inside a box. */
data class ImageFit(val left: Float, val top: Float, val scale: Float, val scaleY: Float = scale)

fun argbHex(argb: Int): String =
    if (argb ushr 24 == 0xFF) {
        String.format(Locale.ROOT, "#%02X%02X%02X", (argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
    } else {
        String.format(Locale.ROOT, "#%02X%02X%02X%02X", (argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, argb ushr 24)
    }

private val colorMath = LocalColorRepository()
private fun channels(argb: Int) = RgbColor((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
private fun alpha(argb: Int): String = BigDecimal.valueOf((argb ushr 24) / 255.0)
    .setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

fun argbRgb(argb: Int): String {
    val rgb = colorMath.rgb(channels(argb))
    return if (argb ushr 24 == 0xFF) rgb else rgb.replace("rgb(", "rgba(").dropLast(1) + ", ${alpha(argb)})"
}

/** Mirrors the colour converter display rules: whole-degree hue, one-decimal percentages. */
fun argbHsl(argb: Int): String {
    val hsl = colorMath.hsl(channels(argb))
    return if (argb ushr 24 == 0xFF) hsl else hsl.replace("hsl(", "hsla(").dropLast(1) + ", ${alpha(argb)})"
}

/** Smallest power-of-two sampling factor that keeps both edges within [maxEdge]. */
fun previewSampleSize(
    width: Int, height: Int, maxEdge: Int = ColorPickerLimits.MAX_PREVIEW_EDGE,
    heapBytes: Long = 256L * 1024 * 1024,
): Int {
    require(width > 0 && height > 0 && maxEdge > 0)
    if (width > ColorPickerLimits.MAX_SOURCE_EDGE || height > ColorPickerLimits.MAX_SOURCE_EDGE ||
        width.toLong() * height > ColorPickerLimits.MAX_SOURCE_PIXELS) {
        throw ColorPickerException(ColorPickerError.TOO_LARGE)
    }
    val budget = minOf(ColorPickerLimits.MAX_DECODE_BYTES, heapBytes / 4)
    var sample = 1
    while (true) {
        val w = (width.toLong() + sample - 1) / sample
        val h = (height.toLong() + sample - 1) / sample
        // ARGB_8888 is a preference, not a guarantee: 16-bit PNG may decode as RGBA_F16.
        // Bound each preview at 8 bytes/pixel so two orientation buffers and the previous
        // preview all fit the same per-image allowance, even when their sizes differ.
        val bytes = w * h * 24 + 8L * 1024 * 1024
        if (w <= maxEdge && h <= maxEdge && bytes <= budget) return sample
        if (w == 1L && h == 1L) throw ColorPickerException(ColorPickerError.MEMORY_LIMIT)
        sample *= 2
    }
}

fun fitImage(boxWidth: Float, boxHeight: Float, bitmapWidth: Int, bitmapHeight: Int): ImageFit? {
    if (!boxWidth.isFinite() || !boxHeight.isFinite() || boxWidth <= 0f || boxHeight <= 0f || bitmapWidth <= 0 || bitmapHeight <= 0) return null
    val scale = min(boxWidth / bitmapWidth, boxHeight / bitmapHeight)
    if (!scale.isFinite() || scale <= 0f) return null
    // PainterModifier aligns rounded sizes, then BitmapPainter draws an integer dstSize.
    // Match both steps, including the independent x/y rounding for non-square images.
    val drawnWidth = (bitmapWidth * scale).roundToInt()
    val drawnHeight = (bitmapHeight * scale).roundToInt()
    if (drawnWidth <= 0 || drawnHeight <= 0) return null
    val left = ((boxWidth.roundToInt() - drawnWidth) / 2f).roundToInt().toFloat()
    val top = ((boxHeight.roundToInt() - drawnHeight) / 2f).roundToInt().toFloat()
    return ImageFit(left, top, drawnWidth.toFloat() / bitmapWidth, drawnHeight.toFloat() / bitmapHeight)
}

fun mapTouch(fit: ImageFit, bitmapWidth: Int, bitmapHeight: Int, touchX: Float, touchY: Float): SamplePoint? {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || !touchX.isFinite() || !touchY.isFinite() ||
        !fit.scale.isFinite() || fit.scale <= 0 || !fit.scaleY.isFinite() || fit.scaleY <= 0 ||
        !fit.left.isFinite() || !fit.top.isFinite()) return null
    val localX = (touchX - fit.left) / fit.scale
    val localY = (touchY - fit.top) / fit.scaleY
    if (localX < 0f || localY < 0f || localX >= bitmapWidth || localY >= bitmapHeight) return null
    val x = floor(localX).toInt().coerceIn(0, bitmapWidth - 1)
    val y = floor(localY).toInt().coerceIn(0, bitmapHeight - 1)
    return SamplePoint(x, y, (x + 0.5f) / bitmapWidth, (y + 0.5f) / bitmapHeight)
}

fun markerCenter(fit: ImageFit, point: SamplePoint): Pair<Float, Float> =
    (fit.left + (point.x + 0.5f) * fit.scale) to (fit.top + (point.y + 0.5f) * fit.scaleY)

/** Newest first, de-duplicated, capped at the palette budget. */
fun pushPalette(palette: List<PickedColor>, color: PickedColor): List<PickedColor> =
    (listOf(color) + palette.filterNot { it.argb == color.argb }).take(ColorPickerLimits.MAX_PALETTE)

interface ColorPickerRepository {
    suspend fun load(uri: String): Result<LoadedImage>
    fun sample(image: LoadedImage, x: Int, y: Int): Int
}

class LoadImageUseCase(private val repository: ColorPickerRepository) {
    suspend operator fun invoke(uri: String): Result<LoadedImage> = repository.load(uri)
}

class SampleColorUseCase(private val repository: ColorPickerRepository) {
    operator fun invoke(image: LoadedImage, point: SamplePoint): PickedColor =
        PickedColor(repository.sample(image, point.x, point.y))
}
