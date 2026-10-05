package com.cangshuo.toolbox.feature.json.data

import com.cangshuo.toolbox.feature.json.domain.JsonFailure
import com.cangshuo.toolbox.feature.json.domain.JsonProcessingException
import com.cangshuo.toolbox.feature.json.domain.JsonProcessingPolicy
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ensureActive

internal class JsonWorkGuard(private val context: CoroutineContext) {
    private val started = System.nanoTime()
    private var steps = 0
    fun checkpoint() {
        context.ensureActive()
        if (System.nanoTime() - started > JsonProcessingPolicy.WORK_BUDGET_NANOS) {
            throw JsonProcessingException(JsonFailure.TIME_BUDGET)
        }
    }
    fun step() { if ((++steps and 1023) == 0) checkpoint() }
    fun ensureActive() { context.ensureActive() }
}

internal class JsonOutput(initialSize: Int, private val guard: JsonWorkGuard) {
    private val builder = StringBuilder(minOf(initialSize, JsonProcessingPolicy.MAX_OUTPUT_LENGTH))
    fun append(text: String, start: Int = 0, end: Int = text.length) {
        guard.checkpoint()
        reserve(end - start)
        builder.append(text, start, end)
    }
    fun append(char: Char) {
        guard.step()
        reserve(1)
        builder.append(char)
    }
    private fun reserve(count: Int) {
        if (builder.length.toLong() + count > JsonProcessingPolicy.MAX_OUTPUT_LENGTH) {
            throw JsonProcessingException(JsonFailure.OUTPUT_LIMIT)
        }
    }
    override fun toString(): String { guard.checkpoint(); return builder.toString() }
}

internal data class JsonTextMetrics(val lines: Int, val bytes: Int)

internal fun measureJsonText(input: String, guard: JsonWorkGuard): JsonTextMetrics {
    var bytes = 0
    var lines = if (input.isEmpty()) 0 else 1
    var index = 0
    while (index < input.length) {
        guard.step()
        val char = input[index]
        if (char.isLowSurrogate() || (char.isHighSurrogate() && input.getOrNull(index + 1)?.isLowSurrogate() != true)) {
            throw JsonProcessingException(JsonFailure.INVALID_UNICODE, index)
        }
        val point = input.codePointAt(index)
        bytes += when { point <= 0x7F -> 1; point <= 0x7FF -> 2; point <= 0xFFFF -> 3; else -> 4 }
        if (char == '\r' || (char == '\n' && (index == 0 || input[index - 1] != '\r'))) lines++
        index += Character.charCount(point)
    }
    return JsonTextMetrics(lines, bytes)
}

internal fun jsonErrorPosition(input: String, offset: Int, guard: JsonWorkGuard): Pair<Int, Int> {
    var line = 1
    var column = 1
    val end = offset.coerceIn(0, input.length)
    for (index in 0 until end) {
        if ((index and 1023) == 0) guard.ensureActive()
        when (input[index]) {
            '\r' -> { line++; column = 1 }
            '\n' -> { if (index == 0 || input[index - 1] != '\r') line++; column = 1 }
            else -> column++
        }
    }
    return line to column
}
