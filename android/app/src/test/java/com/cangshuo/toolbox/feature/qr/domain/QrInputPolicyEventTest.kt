package com.cangshuo.toolbox.feature.qr.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrInputPolicyEventTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val newYork = ZoneId.of("America/New_York")

    private fun event(
        timeMode: QrEventTimeMode = QrEventTimeMode.FLOATING,
        reminder: QrEventReminder = QrEventReminder.NONE,
        startDate: String = "2026-10-08",
        startTime: String = "10:00",
        endDate: String = "2026-10-08",
        endTime: String = "11:00",
    ) = QrInput(
        type = QrContentType.EVENT,
        form = QrFormInput(event = QrEventInput(
            title = "QA, review; \\",
            location = "Room 1",
            description = "line one\nline two",
            startDate = startDate,
            startTime = startTime,
            endDate = endDate,
            endTime = endTime,
            timeMode = timeMode,
            reminder = reminder,
        )),
    )

    @Test
    fun floatingEventKeepsLocalTimeAndOmitsTheAlarmBlock() {
        val input = event()
        assertNull(QrInputPolicy.validate(input, shanghai))
        val content = QrInputPolicy.content(input, shanghai)
        assertTrue(content.startsWith("BEGIN:VEVENT\r\n"))
        assertTrue(content.endsWith("END:VEVENT"))
        assertTrue(content.contains("DTSTART:20261008T100000\r\n"))
        assertTrue(content.contains("DTEND:20261008T110000\r\n"))
        assertFalse(content.contains("T100000Z"))
        assertFalse(content.contains("VALARM"))
        assertTrue(content.contains("SUMMARY:QA\\, review\\; \\\\\r\n"))
        assertTrue(content.contains("DESCRIPTION:line one\\nline two\r\n"))
    }

    @Test
    fun deviceToUtcConvertsTheEnteredWallClockTime() {
        val input = event(timeMode = QrEventTimeMode.DEVICE_TO_UTC)
        assertNull(QrInputPolicy.validate(input, shanghai))
        val content = QrInputPolicy.content(input, shanghai)
        assertTrue(content.contains("DTSTART:20261008T020000Z\r\n"))
        assertTrue(content.contains("DTEND:20261008T030000Z\r\n"))
    }

    @Test
    fun daylightSavingGapIsRejectedOnlyWhenConvertingToUtc() {
        val gap = event(
            timeMode = QrEventTimeMode.DEVICE_TO_UTC,
            startDate = "2026-03-08", startTime = "02:30",
            endDate = "2026-03-08", endTime = "04:00",
        )
        assertEquals(QrFailure.EVENT_TIME_GAP, QrInputPolicy.validate(gap, newYork))
        val floating = event(
            startDate = "2026-03-08", startTime = "02:30",
            endDate = "2026-03-08", endTime = "04:00",
        )
        assertNull(QrInputPolicy.validate(floating, newYork))
    }

    @Test
    fun ambiguousFallBackTimeUsesTheEarlierOffset() {
        val input = event(
            timeMode = QrEventTimeMode.DEVICE_TO_UTC,
            startDate = "2026-11-01", startTime = "01:30",
            endDate = "2026-11-01", endTime = "03:00",
        )
        assertNull(QrInputPolicy.validate(input, newYork))
        assertTrue(QrInputPolicy.content(input, newYork).contains("DTSTART:20261101T053000Z\r\n"))
    }

    @Test
    fun reminderChoicesEmitTheExpectedTrigger() {
        fun trigger(reminder: QrEventReminder): String =
            QrInputPolicy.content(event(reminder = reminder), shanghai)

        assertFalse(trigger(QrEventReminder.NONE).contains("VALARM"))
        assertTrue(trigger(QrEventReminder.AT_START).contains("TRIGGER:PT0S\r\n"))
        assertTrue(trigger(QrEventReminder.MINUTES_5).contains("TRIGGER:-PT5M\r\n"))
        assertTrue(trigger(QrEventReminder.MINUTES_30).contains("TRIGGER:-PT30M\r\n"))
        assertTrue(trigger(QrEventReminder.HOUR_1).contains("TRIGGER:-PT1H\r\n"))
        assertTrue(trigger(QrEventReminder.DAY_1).contains("TRIGGER:-P1D\r\n"))
        val alarm = trigger(QrEventReminder.MINUTES_15)
        assertTrue(alarm.contains("BEGIN:VALARM\r\nACTION:DISPLAY\r\n"))
        assertTrue(alarm.contains("DESCRIPTION:QA\\, review\\; \\\\\r\n"))
        assertTrue(alarm.indexOf("BEGIN:VALARM") < alarm.indexOf("END:VEVENT"))
    }

    @Test
    fun endPairAndOrderAreStillValidated() {
        assertEquals(QrFailure.INVALID_EVENT, QrInputPolicy.validate(event(endDate = ""), shanghai))
        assertEquals(QrFailure.EVENT_RANGE,
            QrInputPolicy.validate(event(endDate = "2026-10-08", endTime = "09:00"), shanghai))
        assertNull(QrInputPolicy.validate(event(endDate = "", endTime = ""), shanghai))
    }
}
