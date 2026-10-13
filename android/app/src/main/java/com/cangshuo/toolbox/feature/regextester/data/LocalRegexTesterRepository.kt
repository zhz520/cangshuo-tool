package com.cangshuo.toolbox.feature.regextester.data

import com.cangshuo.toolbox.feature.regextester.domain.*
import com.cangshuo.toolbox.feature.text.data.BoundedTextBuilder
import com.cangshuo.toolbox.feature.text.data.RegexBudgetPolicy
import com.cangshuo.toolbox.feature.text.data.TextReplacementTemplate
import com.cangshuo.toolbox.feature.text.data.TextWorkGuard
import com.cangshuo.toolbox.feature.text.domain.TextFailure
import com.cangshuo.toolbox.feature.text.domain.TextProcessingException
import com.cangshuo.toolbox.feature.text.domain.TextProcessingPolicy
import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reuses the text tool's preflight, guarded sequences and bounded replacement engine. */
class LocalRegexTesterRepository(private val dispatcher: CoroutineDispatcher = Dispatchers.Default) : RegexTesterRepository {
    private val mutex = Mutex()

    override suspend fun evaluate(request: RegexRequest): RegexOutcome = withContext(dispatcher) {
        mutex.withLock {
            val guard = TextWorkGuard(currentCoroutineContext())
            try {
                validate(request)
                TextProcessingPolicy.validateInput(request.input)
                TextProcessingPolicy.validateFindReplace(request.pattern,
                    request.replacement.takeIf { request.mode == RegexMode.REPLACE } ?: "", true)
                RegexBudgetPolicy.validate(request.pattern, guard)
                var flags = if (request.ignoreCase) Pattern.CASE_INSENSITIVE else 0
                if (request.multiline) flags = flags or Pattern.MULTILINE
                if (request.dotAll) flags = flags or Pattern.DOTALL
                val pattern = Pattern.compile(request.pattern, flags)
                guard.checkpoint()
                if (pattern.programSize() > TextProcessingPolicy.MAX_REGEX_PROGRAM ||
                    pattern.groupCount() > TextProcessingPolicy.MAX_CAPTURE_GROUPS) fail(RegexFailure.TOO_COMPLEX)
                val template = if (request.mode == RegexMode.REPLACE)
                    TextReplacementTemplate.parse(request.replacement, pattern, request.expandGroups, guard) else null
                val fullMatch = pattern.matcher(guard.sequence(request.input)).matches()
                val matcher = pattern.matcher(guard.sequence(request.input))
                val names = pattern.namedGroups().entries.associate { (name, index) -> index to name }
                val matches = mutableListOf<RegexMatch>()
                val output = if (template != null) BoundedTextBuilder(request.input.length, guard) else null
                var search = 0
                var cursor = 0
                var retained = 0L
                while (search <= request.input.length) {
                    guard.checkpoint()
                    if (!matcher.find(search)) break
                    if (matches.size >= RegexLimits.MAX_MATCHES) fail(RegexFailure.TOO_MANY_MATCHES)
                    val start = matcher.start()
                    val end = matcher.end()
                    retained += end - start
                    val captures = (1..pattern.groupCount()).map { index ->
                        val from = matcher.start(index)
                        val to = matcher.end(index)
                        retained += (to - from).coerceAtLeast(0)
                        if (retained > RegexLimits.MAX_RESULT_TEXT) fail(RegexFailure.RESULT_TOO_LARGE)
                        RegexCapture(index, names[index], from, to,
                            if (from < 0) null else request.input.substring(from, to))
                    }
                    if (retained > RegexLimits.MAX_RESULT_TEXT) fail(RegexFailure.RESULT_TOO_LARGE)
                    matches += RegexMatch(start, end, request.input.substring(start, end), captures)
                    if (output != null) {
                        output.append(request.input, cursor, start)
                        template!!.append(output, matcher, request.input, guard)
                        cursor = end
                    }
                    if (!request.global) break
                    if (start == end) {
                        if (end == request.input.length) break
                        // RE2/J find() nudges one char; advance a whole code point for Android text.
                        search = request.input.offsetByCodePoints(end, 1)
                    } else search = end
                }
                output?.append(request.input, cursor, request.input.length)
                guard.checkpoint()
                RegexOutcome.Success(RegexReport(matches.toList(), fullMatch, output?.toString()))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: RegexWorkException) { RegexOutcome.Failure(failure.reason) }
            catch (failure: TextProcessingException) { RegexOutcome.Failure(mapFailure(failure.reason)) }
            catch (_: PatternSyntaxException) { RegexOutcome.Failure(RegexFailure.INVALID_PATTERN) }
            catch (_: StackOverflowError) { RegexOutcome.Failure(RegexFailure.TOO_COMPLEX) }
            catch (_: OutOfMemoryError) { RegexOutcome.Failure(RegexFailure.MEMORY_LIMIT) }
            catch (_: Exception) { RegexOutcome.Failure(RegexFailure.PROCESSING_FAILED) }
        }
    }

    private fun validate(request: RegexRequest) {
        if (request.pattern.isEmpty()) fail(RegexFailure.EMPTY_PATTERN)
        if (request.pattern.length > RegexLimits.MAX_PATTERN) fail(RegexFailure.PATTERN_TOO_LONG)
        if (request.input.length > RegexLimits.MAX_INPUT) fail(RegexFailure.INPUT_TOO_LONG)
        if (request.mode == RegexMode.REPLACE && request.replacement.length > RegexLimits.MAX_REPLACEMENT)
            fail(RegexFailure.REPLACEMENT_TOO_LONG)
    }
    private class RegexWorkException(val reason: RegexFailure) : Exception()
    private fun fail(reason: RegexFailure): Nothing = throw RegexWorkException(reason)
    private fun mapFailure(reason: TextFailure): RegexFailure = when (reason) {
        TextFailure.INPUT_TOO_LONG -> RegexFailure.INPUT_TOO_LONG
        TextFailure.FIND_TOO_LONG -> RegexFailure.PATTERN_TOO_LONG
        TextFailure.REPLACEMENT_TOO_LONG -> RegexFailure.REPLACEMENT_TOO_LONG
        TextFailure.INVALID_UNICODE -> RegexFailure.INVALID_UNICODE
        TextFailure.INVALID_REGEX -> RegexFailure.INVALID_PATTERN
        TextFailure.REGEX_TOO_COMPLEX -> RegexFailure.TOO_COMPLEX
        TextFailure.INVALID_REPLACEMENT -> RegexFailure.INVALID_REPLACEMENT
        TextFailure.OUTPUT_TOO_LONG, TextFailure.TOO_MANY_LINES -> RegexFailure.RESULT_TOO_LARGE
        TextFailure.TIME_BUDGET -> RegexFailure.TIME_BUDGET
        TextFailure.MEMORY_LIMIT -> RegexFailure.MEMORY_LIMIT
        TextFailure.PROCESSING_FAILED -> RegexFailure.PROCESSING_FAILED
    }
}
