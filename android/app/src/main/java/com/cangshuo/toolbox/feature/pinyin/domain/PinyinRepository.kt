package com.cangshuo.toolbox.feature.pinyin.domain

/** One conversion result: the rendered text plus the code-point statistics of the input. */
data class PinyinConversion(
    val text: String,
    val statistics: PinyinStatistics,
)

interface PinyinRepository {
    /** Convert [text] according to [options]; [checkpoint] may throw to abort the work. */
    fun convert(text: String, options: PinyinOptions, checkpoint: PinyinCheckpoint): PinyinConversion
}
