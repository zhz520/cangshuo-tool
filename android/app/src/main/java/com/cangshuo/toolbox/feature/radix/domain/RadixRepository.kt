package com.cangshuo.toolbox.feature.radix.domain

interface RadixRepository {
    /** Converts [rawInput] interpreted in [explicitRadix] (null = auto detect). Returns every common base plus metadata. */
    fun convert(rawInput: String, explicitRadix: Int?, uppercase: Boolean): RadixResult
}
