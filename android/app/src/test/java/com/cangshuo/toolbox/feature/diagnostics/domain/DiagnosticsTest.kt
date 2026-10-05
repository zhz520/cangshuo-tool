package com.cangshuo.toolbox.feature.diagnostics.domain

import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {
    private val now = 1_800_000_000_000L
    @Test fun rejectsNormalExitsOldFutureAndClearedEvents() {
        assertEquals(listOf(ExitEvent(now - 1, ExitReason.ANR)), boundedEvents(listOf(
            now to 10, now + 1 to 4, now - 8 * 86_400_000L to 4, now - 5 to 5, now - 1 to 6), now, now - 3))
    }
    @Test fun mergesJavaFallbackWithOsRecordAndCapsSortedHistory() {
        val raw = (0..19).map { now - it * 10_000 to 4 } + (now - 1 to 4)
        val events = boundedEvents(raw, now, 0)
        assertEquals(10, events.size); assertEquals(now, events.first().timestamp)
        assertEquals(now - 90_000, events.last().timestamp)
    }
    @Test fun keepsDistinctReasonsWithoutRetainingRawDetails() {
        assertEquals(3, boundedEvents(listOf(now to 4, now to 5, now to 6), now, 0).size)
    }
    @Test fun delegatesSameExceptionEvenWhenRecordingFails() {
        val original = IllegalStateException("password or sensitive text")
        val thread = Thread.currentThread(); var calls = 0
        SafeCrashHandler({ throw OutOfMemoryError("private") }, Thread.UncaughtExceptionHandler { actualThread, actualError ->
            assertSame(thread, actualThread); assertSame(original, actualError); calls++
        }).uncaughtException(thread, original)
        assertEquals(1, calls)
    }
    @Test fun recordsOnceBeforeDefaultHandler() {
        val order = mutableListOf<String>()
        SafeCrashHandler({ order += "record" }, Thread.UncaughtExceptionHandler { _, _ -> order += "delegate" })
            .uncaughtException(Thread.currentThread(), RuntimeException())
        assertEquals(listOf("record", "delegate"), order)
    }
}
