package com.cangshuo.toolbox.feature.feedback.domain

import org.junit.Assert.*
import org.junit.Test

class FeedbackPolicyTest {
    @Test fun trimsWhitespaceButPreservesParagraphs() {
        val draft = FeedbackPolicy.draft("BUG", "  第一行\n第二行  ", " ")
        assertEquals("第一行\n第二行", draft.content)
        assertNull(draft.contact)
    }
    @Test fun refusesUnknownTypesEmptyOversizedAndControlCharacters() {
        for ((type, content, contact) in listOf(Triple("BAD", "body", ""), Triple("BUG", "  ", ""),
            Triple("BUG", "x".repeat(2001), ""), Triple("BUG", "body\u0000", ""), Triple("BUG", "body", "x\ny"),
            Triple("OTHER", "body", "x".repeat(129)))) {
            try { FeedbackPolicy.draft(type, content, contact); fail("Expected input rejection") }
            catch (error: FeedbackException) { assertEquals(FeedbackFailure.INPUT, error.failure) }
        }
    }
    @Test fun logsNeverContainUserContent() {
        assertEquals("FeedbackDraft[redacted]", FeedbackPolicy.draft("OTHER", "secret", "secret@example.com").toString())
    }
}
