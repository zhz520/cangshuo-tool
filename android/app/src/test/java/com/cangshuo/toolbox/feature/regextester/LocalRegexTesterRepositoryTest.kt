package com.cangshuo.toolbox.feature.regextester

import com.cangshuo.toolbox.feature.regextester.data.LocalRegexTesterRepository
import com.cangshuo.toolbox.feature.regextester.domain.RegexCapture
import com.cangshuo.toolbox.feature.regextester.domain.RegexFailure
import com.cangshuo.toolbox.feature.regextester.domain.RegexLimits
import com.cangshuo.toolbox.feature.regextester.domain.RegexMatch
import com.cangshuo.toolbox.feature.regextester.domain.RegexMode
import com.cangshuo.toolbox.feature.regextester.domain.RegexOutcome
import com.cangshuo.toolbox.feature.regextester.domain.RegexReport
import com.cangshuo.toolbox.feature.regextester.domain.RegexRequest
import com.cangshuo.toolbox.feature.text.domain.TextProcessingPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalRegexTesterRepositoryTest {
    private fun TestScope.repository() = LocalRegexTesterRepository(StandardTestDispatcher(testScheduler))

    private suspend fun TestScope.report(request: RegexRequest): RegexReport {
        val outcome = repository().evaluate(request)
        assertTrue("Expected success, got $outcome", outcome is RegexOutcome.Success)
        return (outcome as RegexOutcome.Success).report
    }

    private suspend fun TestScope.failure(request: RegexRequest, reason: RegexFailure) {
        assertEquals(RegexOutcome.Failure(reason), repository().evaluate(request))
    }

    private fun match(start: Int, end: Int, value: String) = RegexMatch(start, end, value, emptyList())

    @Test
    fun globalFindReturnsEveryNonOverlappingMatchAndFirstModeReturnsOnlyTheFirst() = runTest {
        val request = RegexRequest(pattern = "ab", input = "ab abXab")
        assertEquals(
            RegexReport(listOf(match(0, 2, "ab"), match(3, 5, "ab"), match(6, 8, "ab")), false, null),
            report(request),
        )
        assertEquals(RegexReport(listOf(match(0, 2, "ab")), false, null), report(request.copy(global = false)))
        assertEquals(listOf(match(0, 2, "aa")), report(RegexRequest(pattern = "aa", input = "aaa")).matches)
    }

    @Test
    fun findingASubstringDoesNotMeanTheEntireInputMatches() = runTest {
        val request = RegexRequest(pattern = "ab", input = "zabz", global = false)
        assertEquals(RegexReport(listOf(match(1, 3, "ab")), false, null), report(request))
        assertEquals(RegexReport(listOf(match(0, 2, "ab")), true, null), report(request.copy(input = "ab")))
        assertEquals(RegexReport(emptyList(), false, null), report(request.copy(pattern = "^ab$")))
    }

    @Test
    fun emptyPatternIsAnErrorButWhitespaceIsAValidPattern() = runTest {
        failure(RegexRequest(input = "anything"), RegexFailure.EMPTY_PATTERN)
        assertEquals(
            RegexReport(listOf(match(0, 1, " "), match(2, 3, " ")), false, null),
            report(RegexRequest(pattern = " ", input = " x ")),
        )
    }

    @Test
    fun nonEmptyPatternsCanMatchEmptyInput() = runTest {
        assertEquals(RegexReport(listOf(match(0, 0, "")), true, null), report(RegexRequest(pattern = "a*")))
        assertEquals(RegexReport(emptyList(), false, null), report(RegexRequest(pattern = "a")))
    }

    @Test
    fun ignoreCaseChangesTheMatchesWithoutChangingTheirOriginalText() = runTest {
        val request = RegexRequest(pattern = "ab", input = "AB ab")
        assertEquals(RegexReport(listOf(match(3, 5, "ab")), false, null), report(request))
        assertEquals(
            RegexReport(listOf(match(0, 2, "AB"), match(3, 5, "ab")), false, null),
            report(request.copy(ignoreCase = true)),
        )
        assertEquals(listOf(match(0, 1, "É")), report(RegexRequest(pattern = "é", input = "É", ignoreCase = true)).matches)
    }

    @Test
    fun multilineAffectsLineAnchorsButNotFullMatch() = runTest {
        val request = RegexRequest(pattern = "^ab$", input = "ab\nx\nab")
        assertEquals(RegexReport(emptyList(), false, null), report(request))
        assertEquals(
            RegexReport(listOf(match(0, 2, "ab"), match(5, 7, "ab")), false, null),
            report(request.copy(multiline = true)),
        )
        assertEquals(RegexReport(emptyList(), false, null), report(request.copy(dotAll = true)))
    }

    @Test
    fun dotAllAllowsDotToConsumeNewlinesIndependentlyOfMultiline() = runTest {
        val request = RegexRequest(pattern = "a.b", input = "a\nb")
        assertEquals(RegexReport(emptyList(), false, null), report(request))
        assertEquals(RegexReport(emptyList(), false, null), report(request.copy(multiline = true)))
        assertEquals(RegexReport(listOf(match(0, 3, "a\nb")), true, null), report(request.copy(dotAll = true)))
    }

    @Test
    fun caseMultilineAndDotAllFlagsCompose() = runTest {
        val request = RegexRequest(pattern = "^a.b$", input = "x\nA\nB\ny", ignoreCase = true, multiline = true, dotAll = true)
        assertEquals(RegexReport(listOf(match(2, 5, "A\nB")), false, null), report(request))
        assertEquals(RegexReport(emptyList(), false, null), report(request.copy(ignoreCase = false)))
        assertEquals(RegexReport(emptyList(), false, null), report(request.copy(multiline = false)))
        assertEquals(RegexReport(emptyList(), false, null), report(request.copy(dotAll = false)))
    }

    @Test
    fun numberedAndNamedCapturesIncludeOffsetsAndUnmatchedGroupsAreNull() = runTest {
        assertEquals(
            RegexReport(
                listOf(
                    RegexMatch(0, 7, "tag-12!", listOf(
                        RegexCapture(1, "word", 0, 3, "tag"),
                        RegexCapture(2, null, 4, 6, "12"),
                        RegexCapture(3, "suffix", 6, 7, "!"),
                    )),
                    RegexMatch(8, 11, "x-3", listOf(
                        RegexCapture(1, "word", 8, 9, "x"),
                        RegexCapture(2, null, 10, 11, "3"),
                        RegexCapture(3, "suffix", -1, -1, null),
                    )),
                ), false, null,
            ),
            report(RegexRequest(pattern = "(?P<word>[A-Za-z]+)-(\\d+)(?P<suffix>!)?", input = "tag-12! x-3")),
        )
    }

    @Test
    fun participatingEmptyCapturesRemainDistinctFromUnmatchedCaptures() = runTest {
        assertEquals(
            RegexReport(listOf(RegexMatch(0, 1, "b", listOf(
                RegexCapture(1, null, 0, 0, ""),
                RegexCapture(2, "optional", -1, -1, null),
            ))), true, null),
            report(RegexRequest(pattern = "(a*)b(?P<optional>c)?", input = "b")),
        )
    }

    @Test
    fun emojiMatchesAndCapturesUseUtf16OffsetsForTheWholeCodePoint() = runTest {
        assertEquals(
            RegexReport(listOf(
                RegexMatch(0, 1, "a", listOf(RegexCapture(1, null, 0, 1, "a"))),
                RegexMatch(1, 3, "😀", listOf(RegexCapture(1, null, 1, 3, "😀"))),
                RegexMatch(3, 4, "b", listOf(RegexCapture(1, null, 3, 4, "b"))),
            ), false, null),
            report(RegexRequest(pattern = "(.)", input = "a😀b")),
        )
    }

    @Test
    fun zeroWidthMatchesAdvanceByCodePointAndIncludeTheEndExactlyOnce() = runTest {
        val request = RegexRequest(pattern = "(?:)", input = "a😀b", mode = RegexMode.REPLACE, replacement = "-")
        assertEquals(
            RegexReport(listOf(match(0, 0, ""), match(1, 1, ""), match(3, 3, ""), match(4, 4, "")), false, "-a-😀-b-"),
            report(request),
        )
        assertEquals(RegexReport(listOf(match(0, 0, "")), false, "-a😀b"), report(request.copy(global = false)))
        assertEquals(listOf(match(0, 0, ""), match(4, 4, "")), report(request.copy(pattern = "^|$", mode = RegexMode.MATCH)).matches)
    }

    @Test
    fun zeroWidthReplacementOfEmptyInputRunsOnce() = runTest {
        assertEquals(
            RegexReport(listOf(match(0, 0, "")), true, "|"),
            report(RegexRequest(pattern = "^$", mode = RegexMode.REPLACE, replacement = "|")),
        )
    }

    @Test
    fun inputBudgetAcceptsTheLimitAndRejectsOneMoreUtf16Unit() = runTest {
        val input = "a".repeat(RegexLimits.MAX_INPUT)
        val request = RegexRequest(pattern = "^a+$", input = input)
        assertEquals(RegexReport(listOf(match(0, input.length, input)), true, null), report(request))
        failure(request.copy(input = input + "a"), RegexFailure.INPUT_TOO_LONG)
    }

    @Test
    fun emojiInputBudgetCountsTwoUtf16UnitsPerEmoji() = runTest {
        val input = "😀".repeat(RegexLimits.MAX_INPUT / 2)
        val request = RegexRequest(pattern = "^.+$", input = input)
        assertEquals(RegexReport(listOf(match(0, RegexLimits.MAX_INPUT, input)), true, null), report(request))
        failure(request.copy(input = input + "😀"), RegexFailure.INPUT_TOO_LONG)
    }

    @Test
    fun patternBudgetAcceptsTheLimitAndRejectsOneMoreUnit() = runTest {
        val pattern = "a".repeat(RegexLimits.MAX_PATTERN)
        val request = RegexRequest(pattern = pattern, input = pattern)
        assertEquals(RegexReport(listOf(match(0, pattern.length, pattern)), true, null), report(request))
        failure(request.copy(pattern = pattern + "a"), RegexFailure.PATTERN_TOO_LONG)
    }

    @Test
    fun replacementBudgetAcceptsTheLimitAndRejectsOneMoreUnit() = runTest {
        val replacement = "x".repeat(RegexLimits.MAX_REPLACEMENT)
        val request = RegexRequest(pattern = "a", input = "a", replacement = replacement, mode = RegexMode.REPLACE)
        assertEquals(RegexReport(listOf(match(0, 1, "a")), true, replacement), report(request))
        failure(request.copy(replacement = replacement + "x"), RegexFailure.REPLACEMENT_TOO_LONG)
    }

    @Test
    fun matchModeDoesNotValidateAnUnusedReplacement() = runTest {
        val request = RegexRequest(pattern = "a", input = "a", replacement = "x".repeat(RegexLimits.MAX_REPLACEMENT + 1))
        val expected = RegexReport(listOf(match(0, 1, "a")), true, null)
        assertEquals(expected, report(request))
        assertEquals(expected, report(request.copy(replacement = "\uD800", expandGroups = true)))
    }

    @Test
    fun incompleteSurrogatePairsAreRejectedInEachUsedTextField() = runTest {
        val request = RegexRequest(pattern = "a", input = "a", replacement = "x", mode = RegexMode.REPLACE)
        for (broken in listOf("\uD800", "\uDC00")) {
            failure(request.copy(input = broken), RegexFailure.INVALID_UNICODE)
            failure(request.copy(pattern = broken), RegexFailure.INVALID_UNICODE)
            failure(request.copy(replacement = broken), RegexFailure.INVALID_UNICODE)
        }
    }

    @Test
    fun resultBudgetIncludesTheTextRetainedByCaptures() = runTest {
        val input = "a".repeat(RegexLimits.MAX_INPUT)
        assertEquals(
            RegexReport(listOf(RegexMatch(0, input.length, input, listOf(RegexCapture(1, null, 0, input.length, input)))), true, null),
            report(RegexRequest(pattern = "(a+)", input = input)),
        )
        // Whole match plus two captures retains 200,001 units although the input is within its limit.
        failure(RegexRequest(pattern = "((a+))", input = "a".repeat(RegexLimits.MAX_RESULT_TEXT / 3 + 1)), RegexFailure.RESULT_TOO_LARGE)
    }

    @Test
    fun resultBudgetAccumulatesAcrossMatchesAndDoesNotReturnTheFirstMatchAsPartialSuccess() = runTest {
        val part = RegexLimits.MAX_RESULT_TEXT / 6
        val input = "a".repeat(part) + " " + "a".repeat(part + 1)
        failure(RegexRequest(pattern = "((a+))", input = input), RegexFailure.RESULT_TOO_LARGE)
        val first = report(RegexRequest(pattern = "((a+))", input = input, global = false))
        assertEquals(listOf(RegexMatch(0, part, "a".repeat(part), listOf(
            RegexCapture(1, null, 0, part, "a".repeat(part)),
            RegexCapture(2, null, 0, part, "a".repeat(part)),
        ))), first.matches)
        assertEquals(false, first.fullMatch)
    }

    @Test
    fun replacementOutputBudgetIncludesUnmatchedTailAndFailsTheEntireOperation() = runTest {
        val replacement = "x".repeat(TextProcessingPolicy.MAX_OUTPUT_LENGTH / 100)
        val request = RegexRequest(pattern = "a", input = "a".repeat(100), replacement = replacement, mode = RegexMode.REPLACE)
        val result = report(request)
        assertEquals("x".repeat(TextProcessingPolicy.MAX_OUTPUT_LENGTH), result.replacement)
        assertEquals(List(100) { match(it, it + 1, "a") }, result.matches)
        assertEquals(false, result.fullMatch)
        failure(request.copy(input = request.input + "!"), RegexFailure.RESULT_TOO_LARGE)
        failure(request.copy(input = request.input + "a"), RegexFailure.RESULT_TOO_LARGE)
    }

    @Test
    fun matchCountAllowsTheLimitButNeverReturnsPartialSuccessBeyondIt() = runTest {
        val request = RegexRequest(pattern = "a", input = "a".repeat(RegexLimits.MAX_MATCHES))
        assertEquals(
            RegexReport(List(RegexLimits.MAX_MATCHES) { match(it, it + 1, "a") }, false, null),
            report(request),
        )
        val overLimit = request.copy(input = request.input + "a")
        failure(overLimit, RegexFailure.TOO_MANY_MATCHES)
        failure(overLimit.copy(mode = RegexMode.REPLACE, replacement = "x"), RegexFailure.TOO_MANY_MATCHES)
        assertEquals(RegexReport(listOf(match(0, 1, "a")), false, null), report(overLimit.copy(global = false)))
    }

    @Test
    fun unsupportedBackreferencesAndLookaroundsReturnTheSameFixedError() = runTest {
        for (pattern in listOf("(a)\\1", "(?P<name>a)\\k<name>", "(?=a)a", "(?!b)a", "(?<=a)b", "(?<!a)b")) {
            failure(RegexRequest(pattern = pattern, input = "ab"), RegexFailure.INVALID_PATTERN)
        }
    }

    @Test
    fun malformedPatternsReturnInvalidPatternRatherThanARawParserException() = runTest {
        for (pattern in listOf("[", "(", "*", "a{2,1}")) {
            failure(RegexRequest(pattern = pattern, input = "a"), RegexFailure.INVALID_PATTERN)
        }
    }

    @Test
    fun nestedUnboundedRepetitionUsesRe2AndHandlesLongNearMisses() = runTest {
        val nearMiss = "a".repeat(25_000) + "!"
        for (pattern in listOf("^(a+)+$", "^(a|aa)+$")) {
            assertEquals(RegexReport(emptyList(), false, null), report(RegexRequest(pattern = pattern, input = nearMiss)))
        }
        assertEquals(
            RegexReport(listOf(RegexMatch(0, 4, "aaaa", listOf(RegexCapture(1, null, 0, 4, "aaaa")))), true, null),
            report(RegexRequest(pattern = "^(a+)+$", input = "aaaa")),
        )
    }

    @Test
    fun sharedRegexBudgetsRejectExcessiveDepthExpansionAndCaptureCount() = runTest {
        val depth = TextProcessingPolicy.MAX_REGEX_DEPTH + 1
        failure(RegexRequest(pattern = "(?:".repeat(depth) + "a" + ")".repeat(depth)), RegexFailure.TOO_COMPLEX)
        failure(RegexRequest(pattern = "(a{100}){100}"), RegexFailure.TOO_COMPLEX)
        failure(RegexRequest(pattern = "(a?)".repeat(TextProcessingPolicy.MAX_CAPTURE_GROUPS + 1)), RegexFailure.TOO_COMPLEX)
        val atLimit = report(RegexRequest(pattern = "(a?)".repeat(TextProcessingPolicy.MAX_CAPTURE_GROUPS)))
        assertEquals(
            RegexReport(listOf(RegexMatch(0, 0, "", List(TextProcessingPolicy.MAX_CAPTURE_GROUPS) {
                RegexCapture(it + 1, null, 0, 0, "")
            })), true, null), atLimit,
        )
    }

    @Test
    fun sharedPreflightDoesNotTreatQuotedOrCharacterClassMetacharactersAsNesting() = runTest {
        val literal = "(".repeat(TextProcessingPolicy.MAX_REGEX_DEPTH + 1) + "{999999}"
        assertEquals(
            RegexReport(listOf(match(0, literal.length, literal)), true, null),
            report(RegexRequest(pattern = "\\Q${literal}\\E", input = literal)),
        )
        assertEquals(RegexReport(listOf(match(0, 2, "()")), true, null), report(RegexRequest(pattern = "[()]+", input = "()")))
        assertEquals(RegexReport(listOf(match(0, 2, "汉字")), true, null), report(RegexRequest(pattern = "\\p{Han}+", input = "汉字")))
    }

    @Test
    fun replacementIsLiteralByDefaultIncludingDollarsAndBackslashes() = runTest {
        val replacement = "${'$'}0/${'$'}1/${'$'}{missing}\\tail\\"
        val request = RegexRequest(pattern = "(a)", input = "a a", mode = RegexMode.REPLACE, replacement = replacement)
        assertEquals(replacement + " " + replacement, report(request).replacement)
        failure(request.copy(expandGroups = true), RegexFailure.INVALID_REPLACEMENT)
    }

    @Test
    fun expandedReplacementSupportsWholeNumberedAndNamedGroupsAndEmptyUnmatchedGroups() = runTest {
        val request = RegexRequest(
            pattern = "(?P<letter>a)(b)?", input = "ab a", mode = RegexMode.REPLACE, expandGroups = true,
            replacement = "${'$'}0|${'$'}1|${'$'}2|${'$'}{letter}",
        )
        val result = report(request)
        assertEquals("ab|a|b|a a|a||a", result.replacement)
        assertEquals(listOf(RegexCapture(1, "letter", 3, 4, "a"), RegexCapture(2, null, -1, -1, null)), result.matches[1].captures)
    }

    @Test
    fun groupNumbersUseTheLongestValidIndexAndLeaveExtraDigitsLiteral() = runTest {
        assertEquals("a2/a", report(RegexRequest(
            pattern = "(a)", input = "a", mode = RegexMode.REPLACE, expandGroups = true,
            replacement = "${'$'}12/${'$'}0",
        )).replacement)
        assertEquals("l/a/abcdefghijkl", report(RegexRequest(
            pattern = "(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)(l)", input = "abcdefghijkl",
            mode = RegexMode.REPLACE, expandGroups = true, replacement = "${'$'}12/${'$'}1/${'$'}0",
        )).replacement)
    }

    @Test
    fun expandedReplacementEscapesTheNextCharacterAndDoesNotInterpretBackslashNAsNewline() = runTest {
        assertEquals("${'$'}1|\\|n|line\na", report(RegexRequest(
            pattern = "(a)", input = "a", mode = RegexMode.REPLACE, expandGroups = true,
            replacement = "\\${'$'}1|\\\\|\\n|line\n${'$'}1",
        )).replacement)
    }

    @Test
    fun invalidReplacementIsValidatedEvenWhenThereAreNoMatches() = runTest {
        val request = RegexRequest(pattern = "(?P<letter>a)", input = "zzz", mode = RegexMode.REPLACE, expandGroups = true)
        for (replacement in listOf("${'$'}", "${'$'}x", "${'$'}2", "${'$'}{missing}", "${'$'}{}", "${'$'}{letter", "\\")) {
            failure(request.copy(replacement = replacement), RegexFailure.INVALID_REPLACEMENT)
            failure(request.copy(input = "a", replacement = replacement), RegexFailure.INVALID_REPLACEMENT)
        }
        assertEquals(RegexReport(emptyList(), false, "zzz"), report(request.copy(replacement = "${'$'}{letter}")))
    }

    @Test
    fun firstReplacementPreservesThePrefixOtherMatchesAndSuffix() = runTest {
        val request = RegexRequest(pattern = "a", input = "pre a a suffix", replacement = "X", mode = RegexMode.REPLACE, global = false)
        assertEquals(RegexReport(listOf(match(4, 5, "a")), false, "pre X a suffix"), report(request))
        assertEquals(
            RegexReport(listOf(match(4, 5, "a"), match(6, 7, "a")), false, "pre X X suffix"),
            report(request.copy(global = true)),
        )
    }

    @Test
    fun cancellingQueuedEvaluationPropagatesCancellationAndTheRepositoryCanBeReused() = runTest {
        val repository = repository()
        val request = RegexRequest(pattern = "a", input = "a")
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.evaluate(request) }
        assertTrue(pending.isActive)
        pending.cancel()
        runCurrent()
        try {
            pending.await()
            fail("Cancellation must not be converted into a RegexOutcome")
        } catch (_: CancellationException) {
            assertTrue(pending.isCancelled)
        }
        assertEquals(RegexOutcome.Success(RegexReport(listOf(match(0, 1, "a")), true, null)), repository.evaluate(request))
    }
}
