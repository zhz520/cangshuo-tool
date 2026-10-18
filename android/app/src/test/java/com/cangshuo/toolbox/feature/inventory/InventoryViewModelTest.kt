package com.cangshuo.toolbox.feature.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.inventory.domain.InventoryCategory
import com.cangshuo.toolbox.feature.inventory.domain.InventoryError
import com.cangshuo.toolbox.feature.inventory.domain.InventoryFilter
import com.cangshuo.toolbox.feature.inventory.domain.InventoryInput
import com.cangshuo.toolbox.feature.inventory.domain.InventoryItem
import com.cangshuo.toolbox.feature.inventory.domain.InventoryLimitException
import com.cangshuo.toolbox.feature.inventory.domain.InventoryPolicy
import com.cangshuo.toolbox.feature.inventory.domain.InventoryRepository
import com.cangshuo.toolbox.feature.inventory.domain.InventorySort
import com.cangshuo.toolbox.feature.inventory.domain.InventoryUseCases
import com.cangshuo.toolbox.feature.inventory.ui.InventoryMessage
import com.cangshuo.toolbox.feature.inventory.ui.InventoryViewModel
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var modelKey = 0
    private val today = LocalDate.of(2026, 10, 18).toEpochDay()

    private class FakeRepository(initial: List<InventoryItem> = emptyList()) : InventoryRepository {
        val items = MutableStateFlow(initial)
        var now = 1_000L
        var todayDay = LocalDate.of(2026, 10, 18).toEpochDay()
        var failWrite = false
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

        override fun observeAll(): Flow<List<InventoryItem>> = items
        override suspend fun find(id: Long): InventoryItem? = items.value.firstOrNull { it.id == id }
        override suspend fun insert(item: InventoryItem): Long {
            if (failWrite) throw IOException("write")
            if (items.value.size >= InventoryPolicy.MAX_ITEMS) throw InventoryLimitException()
            val id = nextId++
            items.value = items.value + item.copy(id = id)
            return id
        }
        override suspend fun update(item: InventoryItem) {
            if (failWrite) throw IOException("write")
            check(items.value.any { it.id == item.id })
            items.value = items.value.map { if (it.id == item.id) item else it }
        }
        override suspend fun delete(id: Long) {
            if (failWrite) throw IOException("write")
            items.value = items.value.filterNot { it.id == id }
        }
        override suspend fun adjustQuantity(id: Long, delta: Int): Boolean {
            if (failWrite) throw IOException("write")
            val current = items.value.firstOrNull { it.id == id } ?: return false
            val quantity = (current.quantity.toLong() + delta)
                .coerceIn(0L, InventoryPolicy.MAX_QUANTITY.toLong()).toInt()
            items.value = items.value.map {
                if (it.id == id) it.copy(quantity = quantity, updatedAt = now) else it
            }
            return true
        }
        override fun nowMillis(): Long = now
        override fun todayEpochDay(): Long = todayDay
    }

    private fun model(repo: FakeRepository = FakeRepository(), saved: SavedStateHandle = SavedStateHandle()) =
        InventoryViewModel(InventoryUseCases(repo), saved).also { models.put("inventory-${modelKey++}", it) }

    private fun item(
        id: Long,
        name: String = "Item $id",
        quantity: Int = 1,
        threshold: Int = 0,
        expiry: Long? = null,
        updated: Long = id,
    ) = InventoryItem(
        id = id,
        name = name,
        notes = "",
        category = InventoryCategory.HOME,
        location = "Desk",
        quantity = quantity,
        unit = "件",
        unitPriceCents = 100,
        lowStockThreshold = threshold,
        purchaseEpochDay = null,
        expiryEpochDay = expiry,
        createdAt = id,
        updatedAt = updated,
    )

    private fun input(
        name: String = "Lamp",
        quantity: String = "2",
        price: String = "19.9",
        expiry: Long? = null,
    ) = InventoryInput(
        name = name,
        notes = "  notes  ",
        category = InventoryCategory.ELECTRONICS,
        location = "  Study  ",
        quantityText = quantity,
        unit = " box ",
        unitPriceText = price,
        lowStockThresholdText = "2",
        purchaseEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
        expiryEpochDay = expiry,
    )

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    @Test fun initialSavedStateAndStatsAreRestored() = runTest {
        val repo = FakeRepository(listOf(
            item(1, "Alpha", quantity = 2, threshold = 3, updated = 20),
            item(2, "Beta", quantity = 9, threshold = 1, expiry = today - 1, updated = 30),
        ))
        val saved = SavedStateHandle(
            mapOf(
                "inventoryFilter" to InventoryFilter.LOW_STOCK.name,
                "inventorySort" to InventorySort.NAME.name,
                "inventoryQuery" to "alpha",
            ),
        )
        val model = model(repo, saved)
        assertTrue(model.state.value.loading)
        runCurrent()
        assertFalse(model.state.value.loading)
        assertEquals(listOf(1L), model.state.value.items.map { it.id })
        assertEquals(InventoryFilter.LOW_STOCK, model.state.value.filter)
        assertEquals(InventorySort.NAME, model.state.value.sort)
        assertEquals(1, model.state.value.stats.lowStockCount)
        assertEquals(1, model.state.value.stats.expiredCount)
        assertEquals(today, model.state.value.todayEpochDay)
    }

    @Test fun createEditNormalizeAndStorageFailureKeepRevision() = runTest {
        val repo = FakeRepository(listOf(item(1, "Old")))
        val model = model(repo)
        runCurrent()
        assertTrue(model.save(null, input(quantity = "12")))
        assertTrue(model.state.value.saving)
        advanceUntilIdle()
        val created = repo.items.value.last()
        assertEquals("Lamp", created.name)
        assertEquals("notes", created.notes)
        assertEquals("Study", created.location)
        assertEquals("box", created.unit)
        assertEquals(1_990L, created.unitPriceCents)
        assertEquals(InventoryMessage.ADDED, model.state.value.message)
        assertEquals(1L, model.state.value.saveSuccessRevision)

        model.consumeMessage()
        assertTrue(model.save(created.id, input(name = "Desk lamp", expiry = today + 5)))
        advanceUntilIdle()
        assertEquals("Desk lamp", repo.items.value.last().name)
        assertEquals(InventoryMessage.UPDATED, model.state.value.message)
        assertEquals(2L, model.state.value.saveSuccessRevision)

        repo.failWrite = true
        assertTrue(model.save(created.id, input(name = "Draft survives")))
        advanceUntilIdle()
        assertEquals(InventoryError.STORAGE, model.state.value.editError)
        assertFalse(model.state.value.saving)
        assertEquals(2L, model.state.value.saveSuccessRevision)
    }

    @Test fun validationLimitAndMissingEditAreRejectedBeforeWriting() = runTest {
        val repo = FakeRepository()
        val model = model(repo)
        runCurrent()
        assertFalse(model.save(null, input(name = " ")))
        assertEquals(InventoryError.NAME, model.state.value.editError)
        model.clearEditError()
        assertNull(model.state.value.editError)
        assertFalse(model.save(null, input(quantity = "1000000")))
        assertEquals(InventoryError.QUANTITY, model.state.value.editError)
        model.clearEditError()
        repo.items.value = List(InventoryPolicy.MAX_ITEMS) { item(it + 1L) }
        runCurrent()
        assertFalse(model.save(null, input()))
        assertEquals(InventoryError.LIMIT, model.state.value.editError)
        assertFalse(model.save(9_999L, input()))
    }

    @Test fun quantityActionsDeduplicateAndDeleteRefreshesState() = runTest {
        val repo = FakeRepository(listOf(item(1, quantity = 2), item(2, name = "Delete me")))
        val model = model(repo)
        runCurrent()
        val first = model.state.value.items.first { it.id == 1L }
        model.adjust(first, 1)
        model.adjust(first, 1)
        assertTrue(1L in model.state.value.busyItemIds)
        advanceUntilIdle()
        assertEquals(3, repo.items.value.first { it.id == 1L }.quantity)
        assertTrue(model.state.value.busyItemIds.isEmpty())

        model.delete(repo.items.value.first { it.id == 2L })
        assertTrue(2L in model.state.value.busyItemIds)
        advanceUntilIdle()
        assertEquals(listOf(1L), repo.items.value.map { it.id })
        assertEquals(InventoryMessage.DELETED, model.state.value.message)
        assertTrue(model.state.value.busyItemIds.isEmpty())
    }

    @Test fun quantityFailureTodayRefreshAndQueryFilterSortMutationsAreReported() = runTest {
        val repo = FakeRepository(listOf(item(1, "Zulu", quantity = 1), item(2, "Alpha", quantity = 5)))
        val model = model(repo)
        runCurrent()
        model.setFilter(InventoryFilter.ALL)
        model.setSort(InventorySort.NAME)
        model.setQuery("a")
        assertEquals(listOf(2L), model.state.value.items.map { it.id })
        assertEquals("a", model.state.value.query)

        repo.failWrite = true
        model.adjust(model.state.value.items.first(), 1)
        advanceUntilIdle()
        assertEquals(InventoryMessage.STORAGE_FAILED, model.state.value.message)
        model.consumeMessage()
        model.delete(model.state.value.items.first())
        advanceUntilIdle()
        assertEquals(InventoryMessage.STORAGE_FAILED, model.state.value.message)

        repo.todayDay = today + 1
        model.refreshToday()
        assertEquals(today + 1, model.state.value.todayEpochDay)
    }
}
