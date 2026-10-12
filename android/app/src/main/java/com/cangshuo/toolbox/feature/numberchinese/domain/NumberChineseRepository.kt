package com.cangshuo.toolbox.feature.numberchinese.domain

interface NumberChineseRepository {
    fun convert(
        input: String,
        style: ChineseNumberStyle,
        direction: NumberDirection,
        colloquialTen: Boolean,
    ): NumberChineseOutcome

    /** AUTO: numbers go to Chinese, anything else is parsed as Chinese numerals. */
    fun detectDirection(input: String): NumberDirection
}
