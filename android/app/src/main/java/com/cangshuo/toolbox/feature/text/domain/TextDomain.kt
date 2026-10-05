package com.cangshuo.toolbox.feature.text.domain

data class TextStatistics(
    val characters: Int = 0,
    val charactersNoSpaces: Int = 0,
    val words: Int = 0,
    val cjkCharacters: Int = 0,
    val lines: Int = 0,
    val nonEmptyLines: Int = 0,
    val bytes: Int = 0,
)

enum class TextCategoryGroup {
    CASE,
    WHITESPACE,
    LINES,
    FIND_REPLACE,
}

enum class TextOperation(val group: TextCategoryGroup) {
    // Case conversions
    UPPERCASE(TextCategoryGroup.CASE),
    LOWERCASE(TextCategoryGroup.CASE),
    TITLE_CASE(TextCategoryGroup.CASE),
    SENTENCE_CASE(TextCategoryGroup.CASE),
    CAMEL_CASE(TextCategoryGroup.CASE),
    PASCAL_CASE(TextCategoryGroup.CASE),
    SNAKE_CASE(TextCategoryGroup.CASE),
    KEBAB_CASE(TextCategoryGroup.CASE),
    CONSTANT_CASE(TextCategoryGroup.CASE),

    // Whitespace & formatting
    TRIM_LINES(TextCategoryGroup.WHITESPACE),
    REMOVE_EMPTY_LINES(TextCategoryGroup.WHITESPACE),
    COLLAPSE_SPACES(TextCategoryGroup.WHITESPACE),
    REMOVE_ALL_SPACES(TextCategoryGroup.WHITESPACE),

    // Line manipulation
    SORT_AZ(TextCategoryGroup.LINES),
    SORT_ZA(TextCategoryGroup.LINES),
    NATURAL_SORT(TextCategoryGroup.LINES),
    DEDUPLICATE(TextCategoryGroup.LINES),
    REVERSE_LINES(TextCategoryGroup.LINES),
    NUMBER_LINES(TextCategoryGroup.LINES),
}

data class TextReplaceResult(val text: String, val matchCount: Int)

enum class TextFailure {
    INPUT_TOO_LONG, FIND_TOO_LONG, REPLACEMENT_TOO_LONG, INVALID_UNICODE,
    INVALID_REGEX, REGEX_TOO_COMPLEX, INVALID_REPLACEMENT, OUTPUT_TOO_LONG,
    TOO_MANY_LINES, TIME_BUDGET, MEMORY_LIMIT, PROCESSING_FAILED,
}

class TextProcessingException(val reason: TextFailure) : Exception()
