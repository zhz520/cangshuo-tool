package com.cangshuo.toolbox.feature.banner.domain

import com.cangshuo.toolbox.core.model.UnicodeText

enum class BannerDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT }

enum class BannerPreset { WELCOME, PICKUP, BIRTHDAY, MAKE_WAY }

data class BannerSettings(
    val text: String = "",
    val speedDpPerSecond: Float = 88f,
    val fontSizeSp: Float = 72f,
    val direction: BannerDirection = BannerDirection.RIGHT_TO_LEFT,
    val mirrored: Boolean = false,
    val keepScreenOn: Boolean = true,
)

enum class BannerError { EMPTY, OUT_OF_RANGE }

interface BannerSettingsRepository {
    fun sanitizeInput(raw: String): String

    fun validate(settings: BannerSettings): BannerError?

    fun visibleCopyPositions(
        startX: Float,
        cycleWidth: Float,
        textWidth: Float,
        viewportWidth: Float,
    ): List<Float>
}

class ApplyBannerSettingsUseCase(private val repository: BannerSettingsRepository) {
    fun sanitizeInput(raw: String): String = repository.sanitizeInput(raw)

    fun validate(settings: BannerSettings): BannerError? = repository.validate(settings)

    fun visibleCopyPositions(
        startX: Float,
        cycleWidth: Float,
        textWidth: Float,
        viewportWidth: Float,
    ): List<Float> = repository.visibleCopyPositions(startX, cycleWidth, textWidth, viewportWidth)
}

object BannerPolicy {
    const val MAX_TEXT_CODE_POINTS = 120
    const val MAX_INPUT_SCAN_UNITS = 4096
    const val MIN_SPEED = 20f
    const val MAX_SPEED = 240f
    const val MIN_FONT_SIZE = 32f
    const val MAX_FONT_SIZE = 140f
    const val GAP_DP = 56f
    const val MAX_COPIES = 128

    fun sanitizeInput(raw: String): String {
        val flattened = buildString {
            raw.take(MAX_INPUT_SCAN_UNITS).forEach { character ->
                when {
                    character == '\n' || character == '\r' || character == '\t' -> append(' ')
                    Character.isISOControl(character) -> Unit
                    Character.getType(character) == Character.FORMAT.toInt() &&
                        character != '\u200C' && character != '\u200D' -> Unit
                    else -> append(character)
                }
            }
        }
        return UnicodeText.takeCodePointsWithoutSplittingCluster(flattened, MAX_TEXT_CODE_POINTS)
    }

    fun isUsable(text: String): Boolean = text.any {
        !it.isWhitespace() && it != '\u200C' && it != '\u200D'
    }

    fun normalize(settings: BannerSettings): BannerSettings = settings.copy(
        text = sanitizeInput(settings.text),
        speedDpPerSecond = settings.speedDpPerSecond.coerceIn(MIN_SPEED, MAX_SPEED),
        fontSizeSp = settings.fontSizeSp.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE),
    )

    fun validate(settings: BannerSettings): BannerError? {
        if (!isUsable(settings.text)) return BannerError.EMPTY
        if (settings.text.codePointCount(0, settings.text.length) > MAX_TEXT_CODE_POINTS) {
            return BannerError.OUT_OF_RANGE
        }
        if (settings.speedDpPerSecond !in MIN_SPEED..MAX_SPEED) return BannerError.OUT_OF_RANGE
        if (settings.fontSizeSp !in MIN_FONT_SIZE..MAX_FONT_SIZE) return BannerError.OUT_OF_RANGE
        return null
    }

    fun motionStartX(
        progressPx: Float,
        cycleWidth: Float,
        textWidth: Float,
        viewportWidth: Float,
        direction: BannerDirection,
    ): Float {
        if (!cycleWidth.isFinite() || cycleWidth <= 0f) return 0f
        val wrapped = positiveModulo(progressPx, cycleWidth)
        return when (direction) {
            BannerDirection.RIGHT_TO_LEFT -> viewportWidth - wrapped
            BannerDirection.LEFT_TO_RIGHT -> -textWidth + wrapped
        }
    }

    /** Copy origins needed to cover the viewport, including partial copies at both edges. */
    fun visibleCopyPositions(
        startX: Float,
        cycleWidth: Float,
        textWidth: Float,
        viewportWidth: Float,
    ): List<Float> {
        if (!startX.isFinite() || !cycleWidth.isFinite() || !textWidth.isFinite() ||
            !viewportWidth.isFinite() || cycleWidth <= 0f || textWidth <= 0f || viewportWidth <= 0f
        ) return emptyList()
        var x = startX
        var guard = 0
        while (x > -textWidth && guard < MAX_COPIES) {
            x -= cycleWidth
            guard++
        }
        x += cycleWidth
        val result = ArrayList<Float>()
        while (x < viewportWidth && result.size < MAX_COPIES) {
            result.add(x)
            x += cycleWidth
        }
        return result
    }

    private fun positiveModulo(value: Float, modulus: Float): Float {
        val remainder = value % modulus
        return if (remainder < 0f) remainder + modulus else remainder
    }
}
