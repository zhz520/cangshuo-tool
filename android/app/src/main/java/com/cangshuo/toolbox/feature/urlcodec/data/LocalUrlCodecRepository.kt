package com.cangshuo.toolbox.feature.urlcodec.data

import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecFailure
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecPolicy
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecRepository
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecResult
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlEncodeType
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalUrlCodecRepository : UrlCodecRepository {
    private val workMutex = Mutex()

    override suspend fun encode(text: String, type: UrlEncodeType): UrlCodecResult = work(text) { output, guard ->
        var index = 0
        while (index < text.length) {
            guard.step()
            val codePoint = text.codePointAt(index)
            when {
                type == UrlEncodeType.FORM_VALUE && codePoint == 0x20 -> output.append('+')
                isSafe(codePoint, type) -> output.append(codePoint.toChar())
                codePoint <= 0x7F -> output.percent(codePoint)
                codePoint <= 0x7FF -> {
                    output.percent(0xC0 or (codePoint ushr 6))
                    output.percent(0x80 or (codePoint and 0x3F))
                }
                codePoint <= 0xFFFF -> {
                    output.percent(0xE0 or (codePoint ushr 12))
                    output.percent(0x80 or ((codePoint ushr 6) and 0x3F))
                    output.percent(0x80 or (codePoint and 0x3F))
                }
                else -> {
                    output.percent(0xF0 or (codePoint ushr 18))
                    output.percent(0x80 or ((codePoint ushr 12) and 0x3F))
                    output.percent(0x80 or ((codePoint ushr 6) and 0x3F))
                    output.percent(0x80 or (codePoint and 0x3F))
                }
            }
            index += Character.charCount(codePoint)
        }
    }

    override suspend fun decode(text: String, type: UrlEncodeType): UrlCodecResult = work(text) { output, guard ->
        var index = 0
        while (index < text.length) {
            guard.step()
            val char = text[index]
            if (char != '%') {
                output.append(if (type == UrlEncodeType.FORM_VALUE && char == '+') ' ' else char)
                index++
                continue
            }
            val start = index
            val first = percentByte(text, index)
            index += 3
            if (first < 0x80) {
                // Preserve escaped URI delimiters, including their original hex case.
                if (type == UrlEncodeType.FULL_URL && first.toChar() in RESERVED) {
                    output.append(text, start, index)
                } else output.append(first.toChar())
                continue
            }
            val width = when (first) {
                in 0xC2..0xDF -> 2
                in 0xE0..0xEF -> 3
                in 0xF0..0xF4 -> 4
                else -> fail(UrlCodecFailure.INVALID_UTF8, start)
            }
            var codePoint = first and (0x7F ushr width)
            repeat(width - 1) {
                guard.step()
                if (index >= text.length || text[index] != '%') fail(UrlCodecFailure.INVALID_UTF8, start)
                val next = percentByte(text, index)
                if (next !in 0x80..0xBF) fail(UrlCodecFailure.INVALID_UTF8, index)
                codePoint = (codePoint shl 6) or (next and 0x3F)
                index += 3
            }
            val minimum = when (width) { 2 -> 0x80; 3 -> 0x800; else -> 0x10000 }
            if (codePoint < minimum || codePoint in 0xD800..0xDFFF || codePoint > 0x10FFFF) {
                fail(UrlCodecFailure.INVALID_UTF8, start)
            }
            output.codePoint(codePoint)
        }
    }

    private suspend fun work(text: String, operation: (UrlOutput, UrlWorkGuard) -> Unit): UrlCodecResult =
        withContext(Dispatchers.Default) {
            workMutex.withLock {
                val guard = UrlWorkGuard(currentCoroutineContext())
                try {
                    guard.checkpoint()
                    if (text.length > UrlCodecPolicy.MAX_INPUT_LENGTH) fail(UrlCodecFailure.INPUT_LIMIT)
                    validateUnicode(text, guard)
                    if (text.isEmpty()) return@withLock UrlCodecResult.Empty
                    val output = UrlOutput(text.length, guard)
                    operation(output, guard)
                    val result = UrlCodecResult.Success(output.toString())
                    guard.checkpoint()
                    result
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: UrlProcessingException) { UrlCodecResult.Error(error.reason, error.offset) }
                catch (_: OutOfMemoryError) { UrlCodecResult.Error(UrlCodecFailure.MEMORY_LIMIT) }
                catch (_: Exception) { UrlCodecResult.Error(UrlCodecFailure.PROCESSING_FAILED) }
            }
        }

    private fun validateUnicode(text: String, guard: UrlWorkGuard) {
        var index = 0
        while (index < text.length) {
            guard.step()
            val char = text[index]
            if (char.isHighSurrogate()) {
                if (index + 1 >= text.length || !text[index + 1].isLowSurrogate()) fail(UrlCodecFailure.INVALID_UNICODE, index)
                index += 2
            } else {
                if (char.isLowSurrogate()) fail(UrlCodecFailure.INVALID_UNICODE, index)
                index++
            }
        }
    }

    private fun percentByte(text: String, index: Int): Int {
        if (index + 2 >= text.length) fail(UrlCodecFailure.INVALID_PERCENT, index)
        val high = hexValue(text[index + 1])
        val low = hexValue(text[index + 2])
        if (high < 0 || low < 0) fail(UrlCodecFailure.INVALID_PERCENT, index)
        return (high shl 4) or low
    }

    private fun hexValue(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'A'..'F' -> char - 'A' + 10
        in 'a'..'f' -> char - 'a' + 10
        else -> -1
    }

    private fun isSafe(codePoint: Int, type: UrlEncodeType): Boolean {
        if (codePoint > 0x7F) return false
        val char = codePoint.toChar()
        val alphanumeric = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9'
        return alphanumeric || when (type) {
            UrlEncodeType.COMPONENT -> char in "-._~"
            UrlEncodeType.FULL_URL -> char in "-._~" || char in RESERVED
            UrlEncodeType.FORM_VALUE -> char in "*-._"
        }
    }

    companion object { private const val RESERVED = ":/?#[]@!$&'()*+,;=" }
}

private class UrlProcessingException(val reason: UrlCodecFailure, val offset: Int?) : Exception()
private fun fail(reason: UrlCodecFailure, offset: Int? = null): Nothing = throw UrlProcessingException(reason, offset)

private class UrlWorkGuard(private val context: CoroutineContext) {
    private val started = System.nanoTime()
    private var steps = 0
    fun step() { if ((++steps and 1023) == 0) checkpoint() }
    fun checkpoint() {
        context.ensureActive()
        if (System.nanoTime() - started > UrlCodecPolicy.WORK_BUDGET_NANOS) fail(UrlCodecFailure.TIME_BUDGET)
    }
}

private class UrlOutput(initialLength: Int, private val guard: UrlWorkGuard) {
    private val builder = StringBuilder(initialLength.coerceAtMost(16_384))
    private fun reserve(length: Int) {
        guard.step()
        if (builder.length.toLong() + length > UrlCodecPolicy.MAX_OUTPUT_LENGTH) fail(UrlCodecFailure.OUTPUT_LIMIT)
    }
    fun append(char: Char) { reserve(1); builder.append(char) }
    fun append(text: String, start: Int, end: Int) { reserve(end - start); builder.append(text, start, end) }
    fun codePoint(value: Int) { reserve(Character.charCount(value)); builder.appendCodePoint(value) }
    fun percent(value: Int) {
        reserve(3)
        builder.append('%').append(HEX[value ushr 4]).append(HEX[value and 15])
    }
    override fun toString(): String = builder.toString()
    companion object { private const val HEX = "0123456789ABCDEF" }
}
