package com.cangshuo.toolbox.feature.qr.data

import com.cangshuo.toolbox.core.database.*
import com.cangshuo.toolbox.feature.qr.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class QrHistoryRepositoryTest {
    private fun result(text: String = "https://example.invalid/qa", type: QrSymbology = QrSymbology.QR_CODE) = QrDecodeEntry(text, type)

    @Test fun optInIsRequiredAndDisablingKeepsExistingEntries() = runTest {
        val dao = MemoryDao()
        val repository = RoomQrHistoryRepository(dao, StandardTestDispatcher(testScheduler)) { 100 }
        assertFalse(repository.observe().first().enabled)
        assertEquals(QrHistoryRecordResult.DISABLED, repository.record(listOf(result())))
        assertTrue(dao.rows.value.isEmpty())
        repository.setEnabled(true)
        assertEquals(QrHistoryRecordResult.SAVED, repository.record(listOf(result())))
        repository.setEnabled(false)
        assertEquals(QrHistoryRecordResult.DISABLED, repository.record(listOf(result("later"))))
        assertEquals(1, repository.observe().first().entries.size)
    }
    @Test fun identicalContentAndFormatUpdateTimeButDifferentFormatsRemainSeparate() = runTest {
        val dao = MemoryDao(true)
        var now = 100L
        val repository = RoomQrHistoryRepository(dao, StandardTestDispatcher(testScheduler)) { now }
        repository.record(listOf(result("abc"), result("abc")))
        now = 200
        repository.record(listOf(result("abc"), result("abc", QrSymbology.CODE_128)))
        val entries = repository.observe().first().entries
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.scannedAt == 200L })
    }
    @Test fun trimsOldRecordsAndRemovalAndClearRetainPreference() = runTest {
        val dao = MemoryDao(true)
        var now = 1L
        val repository = RoomQrHistoryRepository(dao, StandardTestDispatcher(testScheduler)) { now++ }
        repeat(105) { repository.record(listOf(result("record-$it"))) }
        val rows = repository.observe().first().entries
        assertEquals(100, rows.size)
        assertEquals("record-104", rows.first().result.text)
        assertFalse(rows.any { it.result.text == "record-0" })
        repository.remove(rows.first().key)
        assertEquals(99, repository.observe().first().entries.size)
        repository.clear()
        assertTrue(repository.observe().first().entries.isEmpty())
        assertTrue(repository.observe().first().enabled)
    }
    @Test fun sensitiveMalformedAndOversizedContentNeverPersists() = runTest {
        val dao = MemoryDao(true)
        val repository = RoomQrHistoryRepository(dao, StandardTestDispatcher(testScheduler)) { 100 }
        for (text in listOf("WIFI:S:qa;P:synthetic;;", "  otpauth://totp/qa", "otpauth-migration://offline/qa", "", "\uD800", "中".repeat(5334))) {
            assertEquals(QrHistoryRecordResult.SKIPPED, repository.record(listOf(result(text))))
        }
        assertTrue(dao.rows.value.isEmpty())
        assertEquals(QrHistoryRecordResult.SAVED, repository.record(listOf(result("x".repeat(16000)))))
    }
    @Test fun originalWhitespaceAndUnicodeRemainIntactAndBatchBoundIsEnforced() = runTest {
        val dao = MemoryDao(true)
        val repository = RoomQrHistoryRepository(dao, StandardTestDispatcher(testScheduler)) { 100 }
        val entry = result("  中文 😀\nEND  ")
        repository.record(listOf(entry))
        assertEquals(entry, repository.observe().first().entries.single().result)
        assertEquals(QrHistoryRecordResult.SKIPPED, repository.record(List(51) { result("item-$it") }))
        assertEquals(1, dao.rows.value.size)
    }
    @Test fun corruptRowsAreNotExposedAndStorageErrorsHaveNoCause() = runTest {
        val dao = MemoryDao(true)
        val repository = RoomQrHistoryRepository(dao, StandardTestDispatcher(testScheduler)) { 100 }
        repository.record(listOf(result()))
        val valid = dao.rows.value.single()
        dao.rows.value = listOf(valid.copy(payload = "tampered"), valid.copy(symbology = "UNKNOWN_FORMAT"), valid.copy(scannedAt = -1))
        assertTrue(repository.observe().first().entries.isEmpty())
        dao.failure = IllegalStateException("private raw payload")
        try { repository.clear(); fail("Expected failure") } catch (error: QrHistoryException) {
            assertEquals("Scan history unavailable", error.message)
            assertNull(error.cause)
        }
    }
    @Test fun cancellationIsPropagated() = runTest {
        val dao = MemoryDao(true)
        val repository = RoomQrHistoryRepository(dao, StandardTestDispatcher(testScheduler))
        dao.failure = CancellationException("cancelled")
        try { repository.clear(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }
    private class MemoryDao(enabled: Boolean? = null) : QrHistoryDao {
        val preference = MutableStateFlow(enabled)
        val rows = MutableStateFlow(emptyList<QrHistoryEntity>())
        var failure: Exception? = null
        override fun observeEntries() = rows
        override fun observeEnabled() = preference
        override suspend fun isEnabled() = preference.value
        override suspend fun setPreference(preference: QrHistoryPreferenceEntity) { this.preference.value = preference.enabled }
        override suspend fun upsert(entries: List<QrHistoryEntity>) {
            failure?.let { throw it }
            rows.value = (rows.value.filterNot { row -> entries.any { it.key == row.key } } + entries)
                .sortedWith(compareByDescending<QrHistoryEntity> { it.scannedAt }.thenBy { it.key })
        }
        override suspend fun trim() { rows.value = rows.value.take(100) }
        override suspend fun remove(key: String) { rows.value = rows.value.filterNot { it.key == key } }
        override suspend fun clear() { failure?.let { throw it }; rows.value = emptyList() }
    }
}
