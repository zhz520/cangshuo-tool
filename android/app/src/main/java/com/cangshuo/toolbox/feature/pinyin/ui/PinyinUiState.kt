package com.cangshuo.toolbox.feature.pinyin.ui

import com.cangshuo.toolbox.feature.pinyin.domain.PinyinCaseMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinMode
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinOptions
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinOutcome
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinSeparator
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinToneStyle
import com.cangshuo.toolbox.feature.pinyin.domain.PinyinVCharType

data class PinyinUiState(
    val input: String = "",
    val mode: PinyinMode = PinyinMode.FULL,
    val toneStyle: PinyinToneStyle = PinyinToneStyle.TONE_MARK,
    val caseMode: PinyinCaseMode = PinyinCaseMode.LOWER,
    val separator: PinyinSeparator = PinyinSeparator.SPACE,
    val vCharType: PinyinVCharType = PinyinVCharType.UNICODE,
    val keepNonChinese: Boolean = true,
    val processing: Boolean = false,
    val cancelled: Boolean = false,
    val outcome: PinyinOutcome? = null,
) {
    val options: PinyinOptions
        get() = PinyinOptions(
            mode = mode,
            toneStyle = toneStyle,
            caseMode = caseMode,
            separator = separator,
            vCharType = vCharType,
            keepNonChinese = keepNonChinese,
        )
}
