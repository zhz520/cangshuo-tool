package com.cangshuo.toolbox.feature.inventory

import com.cangshuo.toolbox.feature.inventory.domain.ExpiryState
import com.cangshuo.toolbox.feature.inventory.domain.InventoryCategory
import com.cangshuo.toolbox.feature.inventory.domain.InventoryError
import com.cangshuo.toolbox.feature.inventory.domain.InventoryFilter
import com.cangshuo.toolbox.feature.inventory.domain.InventoryInput
import com.cangshuo.toolbox.feature.inventory.domain.InventoryItem
import com.cangshuo.toolbox.feature.inventory.domain.InventoryPolicy
import com.cangshuo.toolbox.feature.inventory.domain.InventorySort
import com.cangshuo.toolbox.feature.inventory.domain.InventoryValidation
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryPolicyTest {
    private val today = LocalDate.of(2026, 10, 18).toEpochDay()

    private fun input(
        name: String = "物品",
        notes: String = "",
        category: InventoryCategory = InventoryCategory.HOME,
        location: String = "",
        quantity: String = "1",
        unit: String = "件",
        price: String = "",
        threshold: String = "0",
        purchase: Long? = null,
        expiry: Long? = null,
    ) = InventoryInput(name, notes, category, location, quantity, unit, price, threshold, purchase, expiry)

    private fun item(
        id: Long,
        name: String = "Item $id",
        notes: String = "",
        location: String = "",
        quantity: Int = 1,
        price: Long? = null,
        threshold: Int = 0,
        expiry: Long? = null,
        updated: Long = id,
    ) = InventoryItem(
        id = id,
        name = name,
        notes = notes,
        category = InventoryCategory.HOME,
        location = location,
        quantity = quantity,
        unit = "件",
        unitPriceCents = price,
        lowStockThreshold = threshold,
        purchaseEpochDay = null,
        expiryEpochDay = expiry,
        createdAt = id,
        updatedAt = updated,
    )

    @Test fun unicodeBudgetsNumbersAndPricesAreValidated() {
        val emoji = "\uD83D\uDE00"
        assertTrue(InventoryPolicy.parseInput(null, input(name = emoji.repeat(80)), 1) is InventoryValidation.Valid)
        assertEquals(
            InventoryError.NAME,
            (InventoryPolicy.parseInput(null, input(name = emoji.repeat(81)), 1) as InventoryValidation.Invalid).error,
        )
        assertEquals(
            InventoryError.NOTES,
            (InventoryPolicy.parseInput(null, input(notes = "x".repeat(501)), 1) as InventoryValidation.Invalid).error,
        )
        assertEquals(
            InventoryError.LOCATION,
            (InventoryPolicy.parseInput(null, input(location = "x".repeat(61)), 1) as InventoryValidation.Invalid).error,
        )
        assertEquals(
            InventoryError.UNIT,
            (InventoryPolicy.parseInput(null, input(unit = emoji.repeat(13)), 1) as InventoryValidation.Invalid).error,
        )
        assertEquals(
            InventoryError.QUANTITY,
            (InventoryPolicy.parseInput(null, input(quantity = "1000000"), 1) as InventoryValidation.Invalid).error,
        )
        assertEquals(
            InventoryError.THRESHOLD,
            (InventoryPolicy.parseInput(null, input(threshold = "-1"), 1) as InventoryValidation.Invalid).error,
        )
        assertEquals(99_999_999L, InventoryPolicy.parsePriceCents("999999.99"))
        assertEquals(1_050L, InventoryPolicy.parsePriceCents("10.5"))
        assertNull(InventoryPolicy.parsePriceCents("1.234"))
        assertNull(InventoryPolicy.parsePriceCents("1000000"))
        assertEquals("¥10.50", InventoryPolicy.formatPrice(1_050L))
    }

    @Test fun normalizedDraftPreservesCreationTimeAndRejectsBadDateRanges() {
        val existing = item(7, updated = 10)
        val parsed = InventoryPolicy.parseInput(
            existing,
            input(name = "  Desk \n Lamp  ", notes = "  first floor  ", location = "  Study  ",
                quantity = "12", unit = "  box ", price = "19.9", threshold = "",
                purchase = LocalDate.of(2026, 1, 2).toEpochDay(),
                expiry = LocalDate.of(2026, 12, 31).toEpochDay()),
            99,
        ) as InventoryValidation.Valid
        assertEquals(7L, parsed.item.id)
        assertEquals("Desk Lamp", parsed.item.name)
        assertEquals("first floor", parsed.item.notes)
        assertEquals("Study", parsed.item.location)
        assertEquals("box", parsed.item.unit)
        assertEquals(1_990L, parsed.item.unitPriceCents)
        assertEquals(0, parsed.item.lowStockThreshold)
        assertEquals(7L, parsed.item.createdAt)
        assertEquals(99L, parsed.item.updatedAt)

        assertEquals(
            InventoryError.DATE,
            (InventoryPolicy.parseInput(
                null,
                input(
                    purchase = LocalDate.of(2026, 10, 20).toEpochDay(),
                    expiry = LocalDate.of(2026, 10, 19).toEpochDay(),
                ),
                1,
            ) as InventoryValidation.Invalid).error,
        )
        assertEquals(
            InventoryError.DATE,
            (InventoryPolicy.parseInput(null, input(purchase = Long.MAX_VALUE), 1) as InventoryValidation.Invalid).error,
        )
        assertEquals(
            InventoryError.DATE,
            (InventoryPolicy.parseInput(null, input(expiry = LocalDate.of(2101, 1, 1).toEpochDay()), 1)
                as InventoryValidation.Invalid).error,
        )
    }

    @Test fun lowStockAndExpiryBoundariesAreInclusive() {
        assertTrue(item(1, quantity = 3, threshold = 3).isLowStock())
        assertFalse(item(2, quantity = 4, threshold = 3).isLowStock())
        assertEquals(ExpiryState.EXPIRED, item(3, expiry = today - 1).expiryState(today))
        assertEquals(ExpiryState.EXPIRING, item(4, expiry = today).expiryState(today))
        assertEquals(ExpiryState.EXPIRING, item(5, expiry = today + 30).expiryState(today))
        assertEquals(ExpiryState.NORMAL, item(6, expiry = today + 31).expiryState(today))
        assertEquals(ExpiryState.NONE, item(7).expiryState(today))
    }

    @Test fun filtersSearchSortsAndStatsAreStable() {
        val alpha = item(1, "Alpha", notes = "cable", location = "Desk", quantity = 2, price = 300, threshold = 2,
            expiry = today + 2, updated = 40)
        val beta = item(2, "Beta", quantity = 8, price = 100, updated = 30)
        val expired = item(3, "Cable", location = "Drawer", quantity = 0, threshold = 1,
            expiry = today - 1, updated = 50)
        val items = listOf(beta, expired, alpha)

        assertEquals(listOf(1L, 3L), InventoryPolicy.filterAndSort(
            items, InventoryFilter.LOW_STOCK, InventorySort.NAME, "", today,
        ).map { it.id })
        assertEquals(listOf(3L), InventoryPolicy.filterAndSort(
            items, InventoryFilter.EXPIRED, InventorySort.UPDATED, "", today,
        ).map { it.id })
        assertEquals(listOf(1L, 3L), InventoryPolicy.filterAndSort(
            items, InventoryFilter.ALL, InventorySort.VALUE, "cable", today,
        ).map { it.id })
        assertEquals(listOf(3L), InventoryPolicy.filterAndSort(
            items, InventoryFilter.ALL, InventorySort.NAME, "drawer", today,
        ).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), InventoryPolicy.filterAndSort(
            items, InventoryFilter.ALL, InventorySort.UPDATED, "", today,
        ).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), InventoryPolicy.filterAndSort(
            items, InventoryFilter.ALL, InventorySort.QUANTITY, "", today,
        ).map { it.id })
        assertEquals(listOf(2L, 1L, 3L), InventoryPolicy.filterAndSort(
            items, InventoryFilter.ALL, InventorySort.VALUE, "", today,
        ).map { it.id })

        val stats = InventoryPolicy.stats(items, today)
        assertEquals(3, stats.itemCount)
        assertEquals(10L, stats.totalUnits)
        assertEquals(1_400L, stats.knownValueCents)
        assertEquals(2, stats.lowStockCount)
        assertEquals(1, stats.expiringCount)
        assertEquals(1, stats.expiredCount)
    }

    @Test fun storageMappingsAndQueryBudgetFailSafely() {
        assertEquals(InventoryCategory.ELECTRONICS, InventoryCategory.fromStorage(0))
        assertEquals(InventoryCategory.OTHER, InventoryCategory.fromStorage(999))
        val query = "\uD83D\uDE00".repeat(81)
        val normalized = InventoryPolicy.normalizeQuery(query)
        assertEquals(80, normalized.codePointCount(0, normalized.length))
    }
}
