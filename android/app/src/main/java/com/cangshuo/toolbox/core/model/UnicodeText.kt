package com.cangshuo.toolbox.core.model

object UnicodeText {
    private const val ZWNJ = 0x200C
    private const val ZWJ = 0x200D
    private const val REGIONAL_START = 0x1F1E6
    private const val REGIONAL_END = 0x1F1FF
    private const val EMOJI_MODIFIER_START = 0x1F3FB
    private const val EMOJI_MODIFIER_END = 0x1F3FF

    /** Bounds user text without cutting through common combining, flag or ZWJ emoji clusters. */
    fun takeCodePointsWithoutSplittingCluster(value: String, maxCodePoints: Int): String {
        if (value.isEmpty() || maxCodePoints <= 0) return ""
        val points = value.codePoints().toArray()
        if (points.size <= maxCodePoints) return value

        var pointIndex = 0
        var used = 0
        var lastCharIndex = 0
        while (pointIndex < points.size) {
            val clusterStart = pointIndex
            var regionalCount = if (isRegional(points[pointIndex])) 1 else 0
            pointIndex++
            while (pointIndex < points.size) {
                val previous = points[pointIndex - 1]
                val current = points[pointIndex]
                if (isRegional(previous) && isRegional(current)) {
                    if (regionalCount % 2 == 1) {
                        regionalCount++
                        pointIndex++
                        continue
                    }
                    break
                }
                val joinsCluster = previous == ZWJ || previous == ZWNJ ||
                    current == ZWJ || current == ZWNJ ||
                    isCombining(current) || isVariationSelector(current) || isEmojiModifier(current)
                if (!joinsCluster) break
                pointIndex++
            }
            val clusterLength = pointIndex - clusterStart
            if (used + clusterLength > maxCodePoints) break
            used += clusterLength
            lastCharIndex = value.offsetByCodePoints(0, pointIndex)
        }
        return value.substring(0, lastCharIndex)
    }

    private fun isRegional(point: Int): Boolean = point in REGIONAL_START..REGIONAL_END

    private fun isEmojiModifier(point: Int): Boolean = point in EMOJI_MODIFIER_START..EMOJI_MODIFIER_END

    private fun isVariationSelector(point: Int): Boolean =
        point in 0xFE00..0xFE0F || point in 0xE0100..0xE01EF

    private fun isCombining(point: Int): Boolean = when (Character.getType(point)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        -> true
        else -> false
    }
}
