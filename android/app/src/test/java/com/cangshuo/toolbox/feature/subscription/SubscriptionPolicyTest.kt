package com.cangshuo.toolbox.feature.subscription

import com.cangshuo.toolbox.feature.subscription.domain.BillingCycle
import com.cangshuo.toolbox.feature.subscription.domain.DueState
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionCategory
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionError
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionFilter
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionInput
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionItem
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionPolicy
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionSort
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionValidation
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionPolicyTest {
    private val today = LocalDate.of(2026, 10, 18).toEpochDay()

    private fun input(
        name: String = "Music Plus",
        provider: String = "Example Music",
        category: SubscriptionCategory = SubscriptionCategory.MUSIC,
        price: String = "19.90",
        cycle: BillingCycle = BillingCycle.MONTHLY,
        customDays: String = "30",
        start: Long = today - 30,
        next: Long = today + 5,
        payment: String = "",
        notes: String = "",
        reminder: Boolean = true,
        reminderDays: String = "7",
        autoRenew: Boolean = true,
        active: Boolean = true,
    ) = SubscriptionInput(
        name, provider, category, price, cycle, customDays, start, next, payment, notes,
        reminder, reminderDays, autoRenew, active,
    )

    private fun item(
        id: Long,
        name: String = "Subscription $id",
        provider: String = "Provider $id",
        price: Long = 1_000,
        cycle: BillingCycle = BillingCycle.MONTHLY,
        customDays: Int = 30,
        next: Long = today + 10,
        autoRenew: Boolean = false,
        active: Boolean = true,
        reminder: Boolean = false,
        reminderDays: Int = 7,
        notes: String = "",
        updated: Long = id,
    ) = SubscriptionItem(
        id, name, provider, SubscriptionCategory.SOFTWARE, price, cycle, customDays,
        today - 60, next, next, "card", notes, reminder, reminderDays, autoRenew, active, id, updated,
    )

    @Test fun unicodePriceAndDateBudgetsAreValidated() {
        val emoji = "\uD83D\uDE00"
        assertTrue(SubscriptionPolicy.parseInput(null, input(name = emoji.repeat(80)), today.toLong()) is SubscriptionValidation.Valid)
        assertEquals(
            SubscriptionError.NAME,
            (SubscriptionPolicy.parseInput(null, input(name = emoji.repeat(81)), 1) as SubscriptionValidation.Invalid).error,
        )
        assertEquals(
            SubscriptionError.PROVIDER,
            (SubscriptionPolicy.parseInput(null, input(provider = " "), 1) as SubscriptionValidation.Invalid).error,
        )
        assertEquals(
            SubscriptionError.NOTES,
            (SubscriptionPolicy.parseInput(null, input(notes = "x".repeat(501)), 1) as SubscriptionValidation.Invalid).error,
        )
        assertEquals(
            SubscriptionError.PRICE,
            (SubscriptionPolicy.parseInput(null, input(price = "0"), 1) as SubscriptionValidation.Invalid).error,
        )
        assertEquals(
            SubscriptionError.CUSTOM_DAYS,
            (SubscriptionPolicy.parseInput(null, input(cycle = BillingCycle.CUSTOM, customDays = "0"), 1)
                as SubscriptionValidation.Invalid).error,
        )
        assertEquals(
            SubscriptionError.REMINDER_DAYS,
            (SubscriptionPolicy.parseInput(null, input(reminderDays = "31"), 1) as SubscriptionValidation.Invalid).error,
        )
        assertEquals(
            SubscriptionError.DATE,
            (SubscriptionPolicy.parseInput(
                null,
                input(start = LocalDate.of(2026, 10, 20).toEpochDay(), next = today),
                1,
            ) as SubscriptionValidation.Invalid).error,
        )
        assertEquals(1_990L, SubscriptionPolicy.parsePriceCents("19.90"))
        assertNull(SubscriptionPolicy.parsePriceCents("1000000"))
        assertNull(SubscriptionPolicy.parsePriceCents("1.234"))
    }

    @Test fun normalizedDraftPreservesCreationAndStoresCustomCycle() {
        val existing = item(9)
        val parsed = SubscriptionPolicy.parseInput(
            existing,
            input(
                name = "  Example \n Music  ",
                provider = "  Example   Inc ",
                cycle = BillingCycle.CUSTOM,
                customDays = "45",
                payment = "  Bank card  ",
                reminderDays = "0",
                autoRenew = false,
                active = false,
            ),
            99,
        ) as SubscriptionValidation.Valid
        assertEquals("Example Music", parsed.item.name)
        assertEquals("Example Inc", parsed.item.provider)
        assertEquals(45, parsed.item.customCycleDays)
        assertEquals(0, parsed.item.reminderDays)
        assertFalse(parsed.item.autoRenew)
        assertFalse(parsed.item.active)
        assertEquals(9L, parsed.item.id)
        assertEquals(9L, parsed.item.createdAt)
        assertEquals(99L, parsed.item.updatedAt)
        assertEquals(today + 5, parsed.item.billingAnchorEpochDay)
    }

    @Test fun monthlyAndYearlyConversionsUseCentsAndRounding() {
        assertEquals(12_000L, SubscriptionPolicy.yearlyCostCents(1_000, BillingCycle.MONTHLY, 30))
        assertEquals(1_000L, SubscriptionPolicy.monthlyCostCents(1_000, BillingCycle.MONTHLY, 30))
        assertEquals(5_200L, SubscriptionPolicy.yearlyCostCents(100, BillingCycle.WEEKLY, 7))
        assertEquals(433L, SubscriptionPolicy.monthlyCostCents(100, BillingCycle.WEEKLY, 7))
        assertEquals(12_000L, SubscriptionPolicy.yearlyCostCents(3_000, BillingCycle.QUARTERLY, 90))
        assertEquals(12_000L, SubscriptionPolicy.yearlyCostCents(6_000, BillingCycle.HALF_YEARLY, 180))
        assertEquals(12_000L, SubscriptionPolicy.yearlyCostCents(12_000, BillingCycle.YEARLY, 365))
        assertEquals(121_748L, SubscriptionPolicy.yearlyCostCents(10_000, BillingCycle.CUSTOM, 30))
        assertEquals(10_146L, SubscriptionPolicy.monthlyCostCents(10_000, BillingCycle.CUSTOM, 30))
    }

    @Test fun rollForwardHandlesMonthEndWithoutDrift() {
        val january = LocalDate.of(2026, 1, 31)
        val monthly = item(1, next = january.toEpochDay())
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), SubscriptionPolicy.nextFuturePayment(monthly, january.toEpochDay()))
        assertEquals(
            LocalDate.of(2026, 3, 31).toEpochDay(),
            SubscriptionPolicy.nextFuturePayment(monthly, LocalDate.of(2026, 3, 1).toEpochDay()),
        )
        val persistedFirst = monthly.copy(nextPaymentEpochDay = LocalDate.of(2026, 2, 28).toEpochDay())
        assertEquals(
            LocalDate.of(2026, 3, 31).toEpochDay(),
            SubscriptionPolicy.nextFuturePayment(persistedFirst, LocalDate.of(2026, 2, 28).toEpochDay()),
        )
        val quarterly = item(2, cycle = BillingCycle.QUARTERLY, next = LocalDate.of(2025, 11, 30).toEpochDay())
        assertEquals(
            LocalDate.of(2026, 11, 30).toEpochDay(),
            SubscriptionPolicy.nextFuturePayment(quarterly, today),
        )
        val custom = item(3, cycle = BillingCycle.CUSTOM, customDays = 10, next = today - 25)
        assertEquals(today + 5, SubscriptionPolicy.nextFuturePayment(custom, today))
    }

    @Test fun dueStatesFiltersAndReminderWindowAreConsistent() {
        val overdue = item(1, next = today - 1)
        val dueSoon = item(2, next = today + 7)
        val autoRenew = item(3, next = today - 1, autoRenew = true)
        val paused = item(4, next = today - 1, active = false)
        assertEquals(DueState.OVERDUE, overdue.dueState(today))
        assertEquals(DueState.DUE_SOON, dueSoon.dueState(today))
        assertEquals(DueState.NORMAL, autoRenew.dueState(today))
        assertEquals(DueState.PAUSED, paused.dueState(today))
        assertTrue(overdue.copy(reminderEnabled = true, reminderDays = 0).isReminderDue(today))
        assertTrue(dueSoon.copy(reminderEnabled = true, reminderDays = 7).isReminderDue(today))
        assertFalse(dueSoon.copy(reminderEnabled = true, reminderDays = 6).isReminderDue(today))
        assertFalse(autoRenew.copy(reminderEnabled = true, reminderDays = 7).isReminderDue(today))
        assertEquals(
            listOf(1L, 2L),
            SubscriptionPolicy.filterAndSort(
                listOf(overdue, dueSoon, item(5, next = today + 30)),
                SubscriptionFilter.DUE,
                SubscriptionSort.NEXT_PAYMENT,
                "",
                today,
            ).map { it.id },
        )
    }

    @Test fun filtersSearchSortsAndStatsAreStable() {
        val alpha = item(1, name = "Alpha", provider = "Cloud Co", price = 3_000, next = today + 3, updated = 20)
        val beta = item(2, name = "Beta", provider = "Video Co", price = 1_000, next = today + 10, updated = 30)
        val paused = item(3, name = "Gamma", price = 8_000, next = today - 1, active = false, updated = 40, notes = "annual gift")
        val items = listOf(beta, paused, alpha)
        assertEquals(listOf(1L), SubscriptionPolicy.filterAndSort(
            items, SubscriptionFilter.ACTIVE, SubscriptionSort.NAME, "cloud", today,
        ).map { it.id })
        assertEquals(listOf(3L), SubscriptionPolicy.filterAndSort(
            items, SubscriptionFilter.PAUSED, SubscriptionSort.NAME, "gift", today,
        ).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), SubscriptionPolicy.filterAndSort(
            items, SubscriptionFilter.ALL, SubscriptionSort.PRICE, "", today,
        ).map { it.id })
        val stats = SubscriptionPolicy.stats(items, today)
        assertEquals(3, stats.totalCount)
        assertEquals(2, stats.activeCount)
        assertEquals(1, stats.pausedCount)
        assertEquals(1, stats.dueSoonCount)
        assertEquals(0, stats.overdueCount)
        assertEquals(4_000L, stats.monthlyCostCents)
        assertEquals(48_000L, stats.yearlyCostCents)
    }
}
