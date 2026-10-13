package com.cangshuo.toolbox.feature.regextester.domain

/** Positions are zero-based UTF-16 offsets, with an exclusive end, like Android text selection. */
data class RegexCapture(val index: Int, val name: String?, val start: Int, val end: Int, val value: String?)
data class RegexMatch(val start: Int, val end: Int, val value: String, val captures: List<RegexCapture>)
data class RegexReport(val matches: List<RegexMatch>, val fullMatch: Boolean, val replacement: String?)
enum class RegexMode { MATCH, REPLACE }
data class RegexRequest(
    val pattern: String = "",
    val input: String = "",
    val replacement: String = "",
    val mode: RegexMode = RegexMode.MATCH,
    val global: Boolean = true,
    val ignoreCase: Boolean = false,
    val multiline: Boolean = false,
    val dotAll: Boolean = false,
    val expandGroups: Boolean = false,
)

enum class RegexFailure {
    EMPTY_PATTERN, INPUT_TOO_LONG, PATTERN_TOO_LONG, REPLACEMENT_TOO_LONG, INVALID_UNICODE,
    INVALID_PATTERN, TOO_COMPLEX, INVALID_REPLACEMENT, TOO_MANY_MATCHES, RESULT_TOO_LARGE,
    TIME_BUDGET, MEMORY_LIMIT, PROCESSING_FAILED,
}
sealed interface RegexOutcome {
    data class Success(val report: RegexReport) : RegexOutcome
    data class Failure(val reason: RegexFailure) : RegexOutcome
}
object RegexLimits {
    const val MAX_INPUT = 100_000
    const val MAX_PATTERN = 512
    const val MAX_REPLACEMENT = 8192
    const val MAX_MATCHES = 500
    const val MAX_RESULT_TEXT = 200_000
}
interface RegexTesterRepository { suspend fun evaluate(request: RegexRequest): RegexOutcome }
class TestRegexUseCase(private val repository: RegexTesterRepository) {
    suspend operator fun invoke(request: RegexRequest): RegexOutcome = repository.evaluate(request)
}

/** Expressions only; samples and labels belong to localized UI resources. */
enum class RegexPreset(val expression: String, val ignoreCase: Boolean = false, val multiline: Boolean = false) {
    EMAIL("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", ignoreCase = true),
    NUMBER("[+-]?[0-9]+(?:\\.[0-9]+)?"),
    URL("https?://[^\\s<>\"']+", ignoreCase = true),
    DATE("(?P<year>[0-9]{4})-(?P<month>[0-9]{2})-(?P<day>[0-9]{2})"),
    KEY_VALUE("(?P<key>[A-Za-z_][A-Za-z0-9_]*)=(?P<value>[^\\s]+)"),
    BLANK_LINE("^[\\t ]*$", multiline = true),
}
