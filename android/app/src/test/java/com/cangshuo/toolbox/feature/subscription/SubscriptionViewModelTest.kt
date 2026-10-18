package com.cangshuo.toolbox.feature.subscription

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.subscription.domain.BillingCycle
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionCategory
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionError
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionFilter
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionInput
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionItem
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionLimitException
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionPolicy
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionReminderScheduler
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionRepository
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionSort
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionUseCases
import com.cangshuo.toolbox.feature.subscription.ui.SubscriptionMessage
import com.cangshuo.toolbox.feature.subscription.ui.SubscriptionViewModel
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
class SubscriptionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var modelKey = 0
    private val today = LocalDate.of(2026, 10, 18).toEpochDay()

    private class FakeScheduler : SubscriptionReminderScheduler {
        var enabled: Boolean? = null
        var calls = 0
        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
            calls++
        }
    }

    private class FakeRepository(initial: List<SubscriptionItem> = emptyList()) : SubscriptionRepository {
        val items = MutableStateFlow(initial)
        var now = 1_000L
        var todayDay = LocalDate.of(2026, 10, 18).toEpochDay()
        var failWrite = false
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

        override fun observeAll(): Flow<List<SubscriptionItem>> = items
        override suspend fun find(id: Long): SubscriptionItem? = items.value.firstOrNull { it.id == id }
        override suspend fun insert(item: SubscriptionItem): Long {
            if (failWrite) throw IOException("write")
            if (items.value.size >= SubscriptionPolicy.MAX_ITEMS) throw SubscriptionLimitException()
            val id = nextId++
            items.value = items.value + item.copy(id = id)
            return id
        }
        override suspend fun update(item: SubscriptionItem) {
            if (failWrite) throw IOException("write")
            check(items.value.any { it.id == item.id })
            items.value = items.value.map { if (it.id == item.id) item else it }
        }
        override suspend fun delete(id: Long) {
            if (failWrite) throw IOException("write")
            items.value = items.value.filterNot { it.id == id }
        }
        override fun nowMillis(): Long = now
        override fun todayEpochDay(): Long = todayDay
    }

    private fun model(
        repo: FakeRepository = FakeRepository(),
        scheduler: FakeScheduler = FakeScheduler(),
        saved: SavedStateHandle = SavedStateHandle(),
    ) = SubscriptionViewModel(
        SubscriptionUseCases(repo, scheduler),
        saved,
    ).also { models.put("subscription-${modelKey++}", it) }

    private fun item(
        id: Long,
        name: String = "Service $id",
        price: Long = 1_000,
        next: Long = today + 10,
        active: Boolean = true,
        reminder: Boolean = false,
        autoRenew: Boolean = false,
        updated: Long = id,
    ) = SubscriptionItem(
        id, name, "Provider $id", SubscriptionCategory.SOFTWARE, price, BillingCycle.MONTHLY, 30,
        today - 30, next, next, "card", "", reminder, 7, autoRenew, active, id, updated,
    )

    private fun input(
        name: String = "Music Plus",
        provider: String = "Example Music",
        price: String = "19.90",
        cycle: BillingCycle = BillingCycle.MONTHLY,
        customDays: String = "30",
        start: Long = today - 30,
        next: Long = today + 5,
        reminder: Boolean = true,
        reminderDays: String = "7",
        autoRenew: Boolean = true,
        active: Boolean = true,
    ) = SubscriptionInput(
        name, provider, SubscriptionCategory.MUSIC, price, cycle, customDays, start, next,
        "card", "notes", reminder, reminderDays, autoRenew, active,
    )

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    @Test fun initialSavedStateStatsAndReminderScheduleAreRestored() = runTest {
        val repo = FakeRepository(listOf(
            item(1, name = "Alpha", next = today + 2, active = true, reminder = true, updated = 20),
            item(2, name = "Beta", next = today - 1, active = false, updated = 30),
        ))
        val scheduler = FakeScheduler()
        val saved = SavedStateHandle(
            mapOf(
                "subscriptionFilter" to SubscriptionFilter.ACTIVE.name,
                "subscriptionSort" to SubscriptionSort.NAME.name,
                "subscriptionQuery" to "alpha",
            ),
        )
        val model = model(repo, scheduler, saved)
        assertTrue(model.state.value.loading)
        runCurrent()
        assertFalse(model.state.value.loading)
        assertEquals(listOf(1L), model.state.value.items.map { it.id })
        assertEquals(1, model.state.value.stats.activeCount)
        assertEquals(1, model.state.value.stats.dueSoonCount)
        assertEquals(true, scheduler.enabled)
        assertEquals(1, scheduler.calls)
        assertEquals(today, model.state.value.todayEpochDay)
    }

    @Test fun createEditStorageFailureAndLimitAreHandled() = runTest {
        val repo = FakeRepository(listOf(item(1, name = "Old")))
        val scheduler = FakeScheduler()
        val model = model(repo, scheduler)
        runCurrent()
        assertTrue(model.save(null, input(customDays = "45")))
        assertTrue(model.state.value.saving)
        advanceUntilIdle()
        val created = repo.items.value.last()
        assertEquals("Music Plus", created.name)
        assertEquals(1_990L, created.priceCents)
        assertEquals(SubscriptionMessage.ADDED, model.state.value.message)
        assertEquals(1L, model.state.value.messageRevision)
        assertEquals(1L, model.state.value.saveSuccessRevision)
        assertTrue(created.reminderEnabled)

        assertTrue(model.save(created.id, input(name = "Edited", active = false, reminder = false)))
        advanceUntilIdle()
        assertEquals("Edited", repo.items.value.last().name)
        assertEquals(SubscriptionMessage.UPDATED, model.state.value.message)

        repo.failWrite = true
        assertTrue(model.save(created.id, input(name = "Draft survives")))
        advanceUntilIdle()
        assertEquals(SubscriptionError.STORAGE, model.state.value.editError)
        assertEquals(2L, model.state.value.saveSuccessRevision)

        repo.items.value = List(SubscriptionPolicy.MAX_ITEMS) { item(it + 1L) }
        runCurrent()
        assertFalse(model.save(null, input()))
        assertEquals(SubscriptionError.LIMIT, model.state.value.editError)
        assertFalse(model.save(9_999L, input()))
    }

    @Test fun invalidCustomCycleAndDatesStayInEditor() = runTest {
        val model = model()
        runCurrent()
        assertFalse(model.save(null, input(name = " ")))
        assertEquals(SubscriptionError.NAME, model.state.value.editError)
        model.clearEditError()
        assertNull(model.state.value.editError)
        assertFalse(model.save(null, input(cycle = BillingCycle.CUSTOM, customDays = "0")))
        assertEquals(SubscriptionError.CUSTOM_DAYS, model.state.value.editError)
        model.clearEditError()
        assertFalse(model.save(null, input(start = today, next = today - 1)))
        assertEquals(SubscriptionError.DATE, model.state.value.editError)
    }

    @Test fun pauseRenewDeleteAndDuplicateActionsWork() = runTest {
        val repo = FakeRepository(listOf(item(1, price = 1_000, next = today - 20)))
        val model = model(repo)
        runCurrent()
        val first = model.state.value.items.first()
        model.setActive(first, false)
        model.setActive(first, false)
        assertTrue(1L in model.state.value.busyItemIds)
        advanceUntilIdle()
        assertFalse(repo.items.value.first().active)
        assertEquals(SubscriptionMessage.PAUSED, model.state.value.message)

        val paused = repo.items.value.first()
        model.setActive(paused, true)
        advanceUntilIdle()
        assertTrue(repo.items.value.first().active)
        assertEquals(SubscriptionMessage.RESUMED, model.state.value.message)

        val renewed = repo.items.value.first()
        model.renew(renewed)
        advanceUntilIdle()
        assertTrue(repo.items.value.first().nextPaymentEpochDay > today)
        assertEquals(SubscriptionMessage.RENEWED, model.state.value.message)

        model.delete(repo.items.value.first())
        advanceUntilIdle()
        assertTrue(repo.items.value.isEmpty())
        assertEquals(SubscriptionMessage.DELETED, model.state.value.message)
        assertEquals(false, repo.items.value.any { it.reminderEnabled })
    }

    @Test fun reminderToggleFailureRefreshAndQuerySortAreReported() = runTest {
        val repo = FakeRepository(listOf(item(1, name = "Zulu", reminder = false), item(2, name = "Alpha")))
        val scheduler = FakeScheduler()
        val model = model(repo, scheduler)
        runCurrent()
        model.setFilter(SubscriptionFilter.ALL)
        model.setSort(SubscriptionSort.NAME)
        model.setQuery("alpha")
        assertEquals(listOf(2L), model.state.value.items.map { it.id })

        val enabled = repo.items.value.first().copy(reminderEnabled = true)
        repo.items.value = listOf(enabled, repo.items.value[1])
        runCurrent()
        assertEquals(true, scheduler.enabled)

        repo.failWrite = true
        model.renew(model.state.value.items.first())
        advanceUntilIdle()
        assertEquals(SubscriptionMessage.STORAGE_FAILED, model.state.value.message)
        assertEquals(1L, model.state.value.messageRevision)
        model.consumeMessage()
        model.delete(model.state.value.items.first())
        advanceUntilIdle()
        assertEquals(SubscriptionMessage.STORAGE_FAILED, model.state.value.message)
        assertEquals(2L, model.state.value.messageRevision)

        repo.todayDay = today + 1
        model.refreshToday()
        assertEquals(today + 1, model.state.value.todayEpochDay)
    }
}
