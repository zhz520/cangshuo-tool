package com.cangshuo.toolbox.feature.text.domain

object TextProcessingPolicy {
    const val MAX_INPUT_LENGTH = 200_000
    const val MAX_OUTPUT_LENGTH = 200_000
    const val MAX_FIND_LENGTH = 4096
    const val MAX_REGEX_LENGTH = 512
    const val MAX_REPLACEMENT_LENGTH = 8192
    const val MAX_LINES = 20_000
    const val MAX_REGEX_DEPTH = 32
    const val MAX_REGEX_EXPANSION = 16_384L
    const val MAX_REGEX_PROGRAM = 8192
    const val MAX_CAPTURE_GROUPS = 32
    const val WORK_BUDGET_NANOS = 2_000_000_000L

    fun validateInput(input: String) {
        validateText(input, MAX_INPUT_LENGTH, TextFailure.INPUT_TOO_LONG)
    }

    fun validateFindReplace(find: String, replacement: String, regex: Boolean) {
        validateText(find, if (regex) MAX_REGEX_LENGTH else MAX_FIND_LENGTH, TextFailure.FIND_TOO_LONG)
        validateText(replacement, MAX_REPLACEMENT_LENGTH, TextFailure.REPLACEMENT_TOO_LONG)
    }

    private fun validateText(value: String, limit: Int, tooLong: TextFailure) {
        if (value.length > limit) throw TextProcessingException(tooLong)
        if (!Charsets.UTF_8.newEncoder().canEncode(value)) throw TextProcessingException(TextFailure.INVALID_UNICODE)
    }
}
