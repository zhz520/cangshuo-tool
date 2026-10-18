package com.cangshuo.toolbox.feature.inventory.domain

import com.cangshuo.toolbox.core.model.UnicodeText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow

enum class InventoryCategory(val storage: Int) {
    ELECTRONICS(0), HOME(1), CLOTHING(2), FOOD(3), MEDICINE(4), DOCUMENT(5), OTHER(6);

    companion object {
        fun fromStorage(value: Int): InventoryCategory = entries.firstOrNull { it.storage == value } ?: OTHER
    }
}

enum class InventoryFilter { ALL, LOW_STOCK, EXPIRING, EXPIRED }
enum class InventorySort { UPDATED, NAME, QUANTITY, VALUE, EXPIRY }
enum class InventoryError { NAME, NOTES, LOCATION, UNIT, QUANTITY, PRICE, THRESHOLD, DATE, LIMIT, STORAGE }
enum class ExpiryState { NONE, NORMAL, EXPIRING, EXPIRED }

data class InventoryItem(
    val id: Long,
    val name: String,
    val notes: String,
    val category: InventoryCategory,
    val location: String,
    val quantity: Int,
    val unit: String,
    val unitPriceCents: Long?,
    val lowStockThreshold: Int,
    val purchaseEpochDay: Long?,
    val expiryEpochDay: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val valueCents: Long?
        get() = unitPriceCents?.let { price -> price * quantity.toLong() }

    fun isLowStock(): Boolean = quantity <= lowStockThreshold

    fun expiryState(todayEpochDay: Long): ExpiryState {
        val expiry = expiryEpochDay ?: return ExpiryState.NONE
        return when {
            expiry < todayEpochDay -> ExpiryState.EXPIRED
            expiry <= todayEpochDay + 30L -> ExpiryState.EXPIRING
            else -> ExpiryState.NORMAL
        }
    }
}

data class InventoryInput(
    val name: String,
    val notes: String,
    val category: InventoryCategory,
    val location: String,
    val quantityText: String,
    val unit: String,
    val unitPriceText: String,
    val lowStockThresholdText: String,
    val purchaseEpochDay: Long?,
    val expiryEpochDay: Long?,
)

data class InventoryStats(
    val itemCount: Int = 0,
    val totalUnits: Long = 0,
    val knownValueCents: Long = 0,
    val lowStockCount: Int = 0,
    val expiringCount: Int = 0,
    val expiredCount: Int = 0,
)

sealed interface InventoryValidation {
    data class Valid(val item: InventoryItem) : InventoryValidation
    data class Invalid(val error: InventoryError) : InventoryValidation
}

interface InventoryRepository {
    fun observeAll(): Flow<List<InventoryItem>>
    suspend fun find(id: Long): InventoryItem?
    suspend fun insert(item: InventoryItem): Long
    suspend fun update(item: InventoryItem)
    suspend fun delete(id: Long)
    suspend fun adjustQuantity(id: Long, delta: Int): Boolean
    fun nowMillis(): Long
    fun todayEpochDay(): Long
}

class InventoryLimitException : IllegalStateException("Inventory item limit reached")

class InventoryUseCases(private val repository: InventoryRepository) {
    fun observeAll() = repository.observeAll()

    fun validate(existing: InventoryItem?, input: InventoryInput): InventoryValidation {
        val now = repository.nowMillis()
        val built = InventoryPolicy.parseInput(existing, input, now)
        if (built is InventoryValidation.Valid) {
            val sizeError = InventoryPolicy.validateItem(built.item)
            if (sizeError != null) return InventoryValidation.Invalid(sizeError)
        }
        return built
    }

    suspend fun save(existing: InventoryItem?, input: InventoryInput): InventoryError? {
        val parsed = validate(existing, input)
        if (parsed is InventoryValidation.Invalid) return parsed.error
        val item = (parsed as InventoryValidation.Valid).item
        return try {
            if (existing == null) repository.insert(item)
            else repository.update(item)
            null
        } catch (_: InventoryLimitException) {
            InventoryError.LIMIT
        } catch (_: Exception) {
            InventoryError.STORAGE
        }
    }

    suspend fun delete(id: Long): InventoryError? = try {
        repository.delete(id)
        null
    } catch (_: Exception) {
        InventoryError.STORAGE
    }

    suspend fun adjust(id: Long, delta: Int): InventoryError? = try {
        if (repository.adjustQuantity(id, delta)) null else InventoryError.STORAGE
    } catch (_: Exception) {
        InventoryError.STORAGE
    }

    fun now() = repository.nowMillis()
    fun today() = repository.todayEpochDay()
}

object InventoryPolicy {
    const val MAX_ITEMS = 1_000
    const val MAX_QUANTITY = 999_999
    const val MAX_NAME_CODE_POINTS = 80
    const val MAX_NOTES_CODE_POINTS = 500
    const val MAX_LOCATION_CODE_POINTS = 60
    const val MAX_UNIT_CODE_POINTS = 12
    const val MAX_PRICE_CENTS = 99_999_999L
    val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
    val MAX_DATE: LocalDate = LocalDate.of(2100, 12, 31)
    private val pricePattern = Regex("\\d{1,7}(?:\\.\\d{1,2})?")
    private val quantityPattern = Regex("\\d{1,6}")

    fun parseInput(existing: InventoryItem?, input: InventoryInput, now: Long): InventoryValidation {
        val name = normalizeText(input.name)
        val notes = input.notes.trim()
        val location = normalizeText(input.location)
        val unit = normalizeText(input.unit)
        if (name.isBlank()) return InventoryValidation.Invalid(InventoryError.NAME)
        if (name.codePointCount(0, name.length) > MAX_NAME_CODE_POINTS) {
            return InventoryValidation.Invalid(InventoryError.NAME)
        }
        if (notes.codePointCount(0, notes.length) > MAX_NOTES_CODE_POINTS) {
            return InventoryValidation.Invalid(InventoryError.NOTES)
        }
        if (location.codePointCount(0, location.length) > MAX_LOCATION_CODE_POINTS) {
            return InventoryValidation.Invalid(InventoryError.LOCATION)
        }
        if (unit.codePointCount(0, unit.length) > MAX_UNIT_CODE_POINTS) {
            return InventoryValidation.Invalid(InventoryError.UNIT)
        }
        val quantity = input.quantityText.trim().takeIf { quantityPattern.matches(it) }?.toIntOrNull()
            ?.takeIf { it in 0..MAX_QUANTITY }
            ?: return InventoryValidation.Invalid(InventoryError.QUANTITY)
        val price = if (input.unitPriceText.isBlank()) null else {
            parsePriceCents(input.unitPriceText) ?: return InventoryValidation.Invalid(InventoryError.PRICE)
        }
        val threshold = input.lowStockThresholdText.trim().ifBlank { "0" }
            .takeIf { quantityPattern.matches(it) }?.toIntOrNull()
            ?.takeIf { it in 0..MAX_QUANTITY }
            ?: return InventoryValidation.Invalid(InventoryError.THRESHOLD)
        val purchase = input.purchaseEpochDay
        val expiry = input.expiryEpochDay
        if (purchase != null && !validDate(purchase)) return InventoryValidation.Invalid(InventoryError.DATE)
        if (expiry != null && !validDate(expiry)) return InventoryValidation.Invalid(InventoryError.DATE)
        if (purchase != null && expiry != null && expiry < purchase) {
            return InventoryValidation.Invalid(InventoryError.DATE)
        }
        val item = InventoryItem(
            id = existing?.id ?: 0,
            name = name,
            notes = notes,
            category = input.category,
            location = location,
            quantity = quantity,
            unit = unit,
            unitPriceCents = price,
            lowStockThreshold = threshold,
            purchaseEpochDay = purchase,
            expiryEpochDay = expiry,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return InventoryValidation.Valid(item)
    }

    fun validateItem(item: InventoryItem): InventoryError? = when {
        item.name.isBlank() || item.name.codePointCount(0, item.name.length) > MAX_NAME_CODE_POINTS -> InventoryError.NAME
        item.notes.codePointCount(0, item.notes.length) > MAX_NOTES_CODE_POINTS -> InventoryError.NOTES
        item.location.codePointCount(0, item.location.length) > MAX_LOCATION_CODE_POINTS -> InventoryError.LOCATION
        item.unit.codePointCount(0, item.unit.length) > MAX_UNIT_CODE_POINTS -> InventoryError.UNIT
        item.quantity !in 0..MAX_QUANTITY -> InventoryError.QUANTITY
        item.unitPriceCents != null && item.unitPriceCents !in 0..MAX_PRICE_CENTS -> InventoryError.PRICE
        item.lowStockThreshold !in 0..MAX_QUANTITY -> InventoryError.THRESHOLD
        item.purchaseEpochDay?.let { !validDate(it) } == true -> InventoryError.DATE
        item.expiryEpochDay?.let { !validDate(it) } == true -> InventoryError.DATE
        item.purchaseEpochDay != null && item.expiryEpochDay != null &&
            item.expiryEpochDay < item.purchaseEpochDay -> InventoryError.DATE
        else -> null
    }

    fun parsePriceCents(text: String): Long? {
        val value = text.trim()
        if (value.isEmpty()) return null
        if (!pricePattern.matches(value)) return null
        return try {
            BigDecimal(value).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact().takeIf { it in 0..MAX_PRICE_CENTS }
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun formatPrice(cents: Long): String =
        String.format(Locale.CHINA, "¥%.2f", BigDecimal.valueOf(cents, 2))

    fun formatQuantity(item: InventoryItem): String =
        if (item.unit.isBlank()) item.quantity.toString() else item.quantity.toString() + " " + item.unit

    fun filterAndSort(
        items: List<InventoryItem>,
        filter: InventoryFilter,
        sort: InventorySort,
        query: String,
        todayEpochDay: Long,
    ): List<InventoryItem> {
        val needle = query.trim().lowercase(Locale.ROOT)
        val visible = items.filter { item ->
            val filterMatches = when (filter) {
                InventoryFilter.ALL -> true
                InventoryFilter.LOW_STOCK -> item.isLowStock()
                InventoryFilter.EXPIRING -> item.expiryState(todayEpochDay) == ExpiryState.EXPIRING
                InventoryFilter.EXPIRED -> item.expiryState(todayEpochDay) == ExpiryState.EXPIRED
            }
            filterMatches && (needle.isEmpty() ||
                item.name.lowercase(Locale.ROOT).contains(needle) ||
                item.notes.lowercase(Locale.ROOT).contains(needle) ||
                item.location.lowercase(Locale.ROOT).contains(needle))
        }
        val name = compareBy<InventoryItem> { it.name.lowercase(Locale.ROOT) }
        return when (sort) {
            InventorySort.UPDATED -> visible.sortedWith(compareByDescending<InventoryItem> { it.updatedAt }.thenByDescending { it.id })
            InventorySort.NAME -> visible.sortedWith(name)
            InventorySort.QUANTITY -> visible.sortedWith(compareBy<InventoryItem> { it.quantity }.then(name))
            InventorySort.VALUE -> visible.sortedWith(compareByDescending<InventoryItem> { it.valueCents ?: -1L }.then(name))
            InventorySort.EXPIRY -> visible.sortedWith(
                compareBy<InventoryItem> { it.expiryEpochDay ?: Long.MAX_VALUE }.then(name),
            )
        }
    }

    fun stats(items: List<InventoryItem>, todayEpochDay: Long): InventoryStats = InventoryStats(
        itemCount = items.size,
        totalUnits = items.sumOf { it.quantity.toLong() },
        knownValueCents = items.sumOf { it.valueCents ?: 0L },
        lowStockCount = items.count { it.isLowStock() },
        expiringCount = items.count { it.expiryState(todayEpochDay) == ExpiryState.EXPIRING },
        expiredCount = items.count { it.expiryState(todayEpochDay) == ExpiryState.EXPIRED },
    )

    fun normalizeQuery(query: String): String =
        UnicodeText.takeCodePointsWithoutSplittingCluster(query, MAX_NAME_CODE_POINTS)

    private fun normalizeText(text: String) = text.trim().replace(Regex("\\s+"), " ")

    private fun validDate(epochDay: Long): Boolean {
        val date = try {
            LocalDate.ofEpochDay(epochDay)
        } catch (_: java.time.DateTimeException) {
            return false
        }
        return !date.isBefore(MIN_DATE) && !date.isAfter(MAX_DATE)
    }
}
