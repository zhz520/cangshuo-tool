package com.cangshuo.toolbox.feature.uuid.domain

interface UuidRepository {
    /** Returns canonical lowercase hyphenated UUID v4 strings; count is clamped to the supported range. */
    fun generateRaw(count: Int): List<String>

    /** Applies uppercase, hyphen and brace presentation options without regenerating. */
    fun format(raw: String, config: UuidConfig): String
}
