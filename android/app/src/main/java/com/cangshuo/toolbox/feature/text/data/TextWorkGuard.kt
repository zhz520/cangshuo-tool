package com.cangshuo.toolbox.feature.text.data

import com.cangshuo.toolbox.feature.text.domain.TextFailure
import com.cangshuo.toolbox.feature.text.domain.TextProcessingException
import com.cangshuo.toolbox.feature.text.domain.TextProcessingPolicy
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ensureActive

internal class TextWorkGuard(private val context: CoroutineContext) {
    private val started = System.nanoTime()
    private var steps = 0

    fun checkpoint() {
        context.ensureActive()
        if (System.nanoTime() - started > TextProcessingPolicy.WORK_BUDGET_NANOS) {
            throw TextProcessingException(TextFailure.TIME_BUDGET)
        }
    }

    fun step() { if ((++steps and 1023) == 0) checkpoint() }

    fun sequence(input: String): CharSequence = CheckedSequence(input, 0, input.length, this)

    private class CheckedSequence(
        private val input: String, private val start: Int, private val end: Int, private val guard: TextWorkGuard,
    ) : CharSequence {
        override val length: Int get() { guard.checkpoint(); return end - start }
        override fun get(index: Int): Char {
            if (index !in 0 until end - start) throw IndexOutOfBoundsException()
            guard.step()
            return input[start + index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            require(startIndex >= 0 && endIndex >= startIndex && endIndex <= end - start)
            guard.checkpoint()
            return CheckedSequence(input, start + startIndex, start + endIndex, guard)
        }
        override fun toString(): String { guard.checkpoint(); return input.substring(start, end) }
    }
}

internal class BoundedTextBuilder(initialSize: Int, private val guard: TextWorkGuard) {
    private val builder = StringBuilder(minOf(initialSize, TextProcessingPolicy.MAX_OUTPUT_LENGTH))
    fun append(value: String, start: Int = 0, end: Int = value.length) {
        guard.checkpoint()
        if (builder.length.toLong() + end - start > TextProcessingPolicy.MAX_OUTPUT_LENGTH) {
            throw TextProcessingException(TextFailure.OUTPUT_TOO_LONG)
        }
        builder.append(value, start, end)
    }
    fun appendCodePoint(codePoint: Int) {
        guard.step()
        if (builder.length + Character.charCount(codePoint) > TextProcessingPolicy.MAX_OUTPUT_LENGTH) {
            throw TextProcessingException(TextFailure.OUTPUT_TOO_LONG)
        }
        builder.appendCodePoint(codePoint)
    }
    override fun toString(): String { guard.checkpoint(); return builder.toString() }
}
