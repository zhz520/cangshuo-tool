package com.cangshuo.toolbox.feature.password.domain

data class PasswordOptions(
    val length: Int,
    val uppercase: Boolean,
    val lowercase: Boolean,
    val digits: Boolean,
    val symbols: Boolean,
    val excludeSimilar: Boolean,
    val excludeText: String,
    val requireEachClass: Boolean,
    val uniqueCharacters: Boolean,
)

data class GeneratedPassword(val value: String, val entropyBits: Double)

enum class PasswordError { NO_CHARSET, LENGTH_RANGE, COUNT_RANGE, STRICT_LENGTH, UNIQUE_IMPOSSIBLE, INPUT_TOO_LONG }

sealed interface PasswordOutcome {
    data class Success(val passwords: List<GeneratedPassword>, val poolSize: Int) : PasswordOutcome

    data class Failure(val error: PasswordError) : PasswordOutcome
}

object PasswordLimits {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 64
    const val MIN_COUNT = 1
    const val MAX_COUNT = 10
    const val MAX_EXCLUDE_LENGTH = 32

    /** Same ambiguous set as the reference implementation: i l L I | ` o O 0 (and 1). */
    const val SIMILAR_CHARACTERS = "ilLI|`oO01"

    const val SYMBOLS = "!@#$%^&*()+_-=}{[]|:;\"/?.><,`~"
}
