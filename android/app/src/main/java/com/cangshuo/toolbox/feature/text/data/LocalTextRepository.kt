package com.cangshuo.toolbox.feature.text.data

import com.cangshuo.toolbox.feature.text.domain.TextFailure
import com.cangshuo.toolbox.feature.text.domain.TextOperation
import com.cangshuo.toolbox.feature.text.domain.TextProcessingException
import com.cangshuo.toolbox.feature.text.domain.TextProcessingPolicy
import com.cangshuo.toolbox.feature.text.domain.TextReplaceResult
import com.cangshuo.toolbox.feature.text.domain.TextRepository
import com.cangshuo.toolbox.feature.text.domain.TextStatistics
import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalTextRepository : TextRepository {
    private val workMutex = Mutex()

    override suspend fun computeStatistics(input: String): Result<TextStatistics> = work(input) { guard ->
        if (input.isEmpty()) return@work TextStatistics()
        var index = 0
        var characters = 0
        var noSpaces = 0
        var cjk = 0
        var bytes = 0
        var words = 0
        var inWord = false
        var lines = 1
        var nonEmptyLines = 0
        var nonBlank = false
        while (index < input.length) {
            guard.step()
            val point = input.codePointAt(index)
            val width = Character.charCount(point)
            characters++
            val whitespace = Character.isWhitespace(point) || Character.isSpaceChar(point)
            if (!whitespace) { noSpaces++; nonBlank = true }
            if (Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN) cjk++
            bytes += when { point <= 0x7F -> 1; point <= 0x7FF -> 2; point <= 0xFFFF -> 3; else -> 4 }
            val asciiWord = point in 'a'.code..'z'.code || point in 'A'.code..'Z'.code || point in '0'.code..'9'.code || point == '_'.code
            if (asciiWord) { if (!inWord) words++; inWord = true }
            else if (point != '\''.code && point != '-'.code) inWord = false
            if (point == '\r'.code || point == '\n'.code) {
                if (point != '\n'.code || index == 0 || input[index - 1] != '\r') {
                    lines++
                    if (nonBlank) nonEmptyLines++
                    nonBlank = false
                }
            }
            index += width
        }
        if (nonBlank) nonEmptyLines++
        TextStatistics(characters, noSpaces, words, cjk, lines, nonEmptyLines, bytes)
    }

    override suspend fun transform(input: String, operation: TextOperation): Result<String> = work(input) { guard ->
        if (input.isEmpty()) return@work ""
        val output = BoundedTextBuilder(input.length, guard)
        when (operation) {
            TextOperation.UPPERCASE -> output.append(input.uppercase(Locale.ROOT))
            TextOperation.LOWERCASE -> output.append(input.lowercase(Locale.ROOT))
            TextOperation.TITLE_CASE -> caseText(input, output, guard, title = true)
            TextOperation.SENTENCE_CASE -> caseText(input, output, guard, title = false)
            TextOperation.COLLAPSE_SPACES, TextOperation.REMOVE_ALL_SPACES -> {
                var previousSpace = false
                for (char in input) {
                    guard.step()
                    val space = char == ' ' || char == '\t'
                    if (!space || (operation == TextOperation.COLLAPSE_SPACES && !previousSpace)) {
                        output.appendCodePoint(if (space) ' '.code else char.code)
                    }
                    previousSpace = space
                }
            }
            else -> {
                var lines = splitLines(input, guard)
                lines = when (operation) {
                    TextOperation.REMOVE_EMPTY_LINES -> lines.filter { guard.checkpoint(); it.isNotBlank() }
                    TextOperation.SORT_AZ -> lines.sortedWith { a, b -> guard.checkpoint(); a.compareTo(b) }
                    TextOperation.SORT_ZA -> lines.sortedWith { a, b -> guard.checkpoint(); b.compareTo(a) }
                    TextOperation.NATURAL_SORT -> lines.sortedWith { a, b -> naturalCompare(a, b, guard) }
                    TextOperation.DEDUPLICATE -> lines.distinct()
                    TextOperation.REVERSE_LINES -> lines.reversed()
                    else -> lines
                }
                for ((index, line) in lines.withIndex()) {
                    guard.checkpoint()
                    if (index != 0) output.append("\n")
                    when (operation) {
                        TextOperation.TRIM_LINES -> output.append(line.trim())
                        TextOperation.NUMBER_LINES -> { output.append("${index + 1}. "); output.append(line) }
                        TextOperation.CAMEL_CASE, TextOperation.PASCAL_CASE, TextOperation.SNAKE_CASE,
                        TextOperation.KEBAB_CASE, TextOperation.CONSTANT_CASE -> naming(line, operation, output, guard)
                        else -> output.append(line)
                    }
                }
            }
        }
        output.toString()
    }

    override suspend fun findAndReplace(
        input: String, find: String, replace: String, matchCase: Boolean, useRegex: Boolean,
        expandGroups: Boolean, multiline: Boolean, dotAll: Boolean,
    ): Result<TextReplaceResult> = work(input) { guard ->
        TextProcessingPolicy.validateFindReplace(find, replace, useRegex)
        if (find.isEmpty()) return@work TextReplaceResult(input, 0)
        if (useRegex) RegexBudgetPolicy.validate(find, guard)
        var flags = if (matchCase) 0 else Pattern.CASE_INSENSITIVE
        if (useRegex && multiline) flags = flags or Pattern.MULTILINE
        if (useRegex && dotAll) flags = flags or Pattern.DOTALL
        val pattern = Pattern.compile(if (useRegex) find else Pattern.quote(find), flags)
        guard.checkpoint()
        if (pattern.programSize() > TextProcessingPolicy.MAX_REGEX_PROGRAM ||
            pattern.groupCount() > TextProcessingPolicy.MAX_CAPTURE_GROUPS) {
            throw TextProcessingException(TextFailure.REGEX_TOO_COMPLEX)
        }
        val template = TextReplacementTemplate.parse(replace, pattern, useRegex && expandGroups, guard)
        val matcher = pattern.matcher(guard.sequence(input))
        val output = BoundedTextBuilder(input.length, guard)
        var cursor = 0
        var search = 0
        var count = 0
        while (search <= input.length) {
            guard.checkpoint()
            if (!matcher.find(search)) break
            val start = matcher.start()
            val end = matcher.end()
            output.append(input, cursor, start)
            template.append(output, matcher, input, guard)
            cursor = end
            count++
            if (start == end) {
                if (end == input.length) break
                // Advance one code point, preserving surrogate pairs for zero-width matches.
                search = input.offsetByCodePoints(end, 1)
            } else search = end
        }
        output.append(input, cursor, input.length)
        TextReplaceResult(output.toString(), count)
    }

    private suspend fun <T> work(input: String, block: (TextWorkGuard) -> T): Result<T> = withContext(Dispatchers.Default) {
        workMutex.withLock {
            val guard = TextWorkGuard(currentCoroutineContext())
            try {
                guard.checkpoint()
                TextProcessingPolicy.validateInput(input)
                val result = block(guard)
                guard.checkpoint()
                Result.success(result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: TextProcessingException) { Result.failure(error) }
            catch (_: PatternSyntaxException) { Result.failure(TextProcessingException(TextFailure.INVALID_REGEX)) }
            catch (_: StackOverflowError) { Result.failure(TextProcessingException(TextFailure.REGEX_TOO_COMPLEX)) }
            catch (_: OutOfMemoryError) { Result.failure(TextProcessingException(TextFailure.MEMORY_LIMIT)) }
            catch (_: Exception) { Result.failure(TextProcessingException(TextFailure.PROCESSING_FAILED)) }
        }
    }

    private fun splitLines(input: String, guard: TextWorkGuard): List<String> {
        val lines = mutableListOf<String>()
        var start = 0
        var index = 0
        while (index < input.length) {
            guard.step()
            if (input[index] == '\r' || input[index] == '\n') {
                if (lines.size >= TextProcessingPolicy.MAX_LINES - 1) throw TextProcessingException(TextFailure.TOO_MANY_LINES)
                lines.add(input.substring(start, index))
                if (input[index] == '\r' && input.getOrNull(index + 1) == '\n') index++
                start = index + 1
            }
            index++
        }
        lines.add(input.substring(start))
        return lines
    }

    private fun caseText(input: String, output: BoundedTextBuilder, guard: TextWorkGuard, title: Boolean) {
        var capitalize = true
        var index = 0
        while (index < input.length) {
            guard.step()
            val point = input.codePointAt(index)
            val letter = Character.isLetter(point)
            val text = String(Character.toChars(point))
            output.append(when {
                capitalize && letter -> text.uppercase(Locale.ROOT)
                title -> text.lowercase(Locale.ROOT)
                else -> text
            })
            if (title) capitalize = Character.isWhitespace(point) ||
                (point <= Char.MAX_VALUE.code && point.toChar() in "-_/()[]{}:;.,")
            else if (letter && capitalize) capitalize = false
            if (!title && (point in listOf('.'.code, '!'.code, '?'.code, '\n'.code, '\r'.code, '。'.code, '！'.code, '？'.code))) capitalize = true
            index += Character.charCount(point)
        }
    }

    private fun naming(line: String, operation: TextOperation, output: BoundedTextBuilder, guard: TextWorkGuard) {
        val words = mutableListOf<String>()
        var start = -1
        var index = 0
        var previous = -1
        while (index < line.length) {
            guard.step()
            val point = line.codePointAt(index)
            val width = Character.charCount(point)
            val next = if (index + width < line.length) line.codePointAt(index + width) else -1
            if (!Character.isLetterOrDigit(point)) {
                if (start >= 0) words.add(line.substring(start, index))
                start = -1
            } else {
                val boundary = start >= 0 && (
                    (Character.isLowerCase(previous) && Character.isUpperCase(point)) ||
                    (Character.isDigit(previous) != Character.isDigit(point)) ||
                    (Character.isUpperCase(previous) && Character.isUpperCase(point) && Character.isLowerCase(next)))
                if (boundary) { words.add(line.substring(start, index)); start = index }
                if (start < 0) start = index
            }
            previous = point
            index += width
        }
        if (start >= 0) words.add(line.substring(start))
        if (words.isEmpty()) { output.append(line); return }
        for ((wordIndex, word) in words.withIndex()) {
            guard.checkpoint()
            val separator = when (operation) {
                TextOperation.SNAKE_CASE, TextOperation.CONSTANT_CASE -> "_"
                TextOperation.KEBAB_CASE -> "-"
                else -> ""
            }
            if (wordIndex != 0) output.append(separator)
            val normalized = if (operation == TextOperation.CONSTANT_CASE) word.uppercase(Locale.ROOT) else word.lowercase(Locale.ROOT)
            if (operation == TextOperation.PASCAL_CASE || (operation == TextOperation.CAMEL_CASE && wordIndex > 0)) {
                val width = Character.charCount(normalized.codePointAt(0))
                output.append(normalized.substring(0, width).uppercase(Locale.ROOT))
                output.append(normalized, width, normalized.length)
            } else output.append(normalized)
        }
    }

    private fun naturalCompare(left: String, right: String, guard: TextWorkGuard): Int {
        guard.checkpoint()
        var a = 0
        var b = 0
        while (a < left.length && b < right.length) {
            guard.step()
            if (left[a] in '0'..'9' && right[b] in '0'..'9') {
                var endA = a
                var endB = b
                while (endA < left.length && left[endA] in '0'..'9') { guard.step(); endA++ }
                while (endB < right.length && right[endB] in '0'..'9') { guard.step(); endB++ }
                while (a < endA - 1 && left[a] == '0') { guard.step(); a++ }
                while (b < endB - 1 && right[b] == '0') { guard.step(); b++ }
                val lengthOrder = (endA - a).compareTo(endB - b)
                if (lengthOrder != 0) return lengthOrder
                while (a < endA) {
                    guard.step()
                    val order = left[a++].compareTo(right[b++])
                    if (order != 0) return order
                }
                a = endA
                b = endB
            } else {
                val order = left[a++].uppercaseChar().lowercaseChar().compareTo(right[b++].uppercaseChar().lowercaseChar())
                if (order != 0) return order
            }
        }
        return (left.length - a).compareTo(right.length - b)
    }
}
