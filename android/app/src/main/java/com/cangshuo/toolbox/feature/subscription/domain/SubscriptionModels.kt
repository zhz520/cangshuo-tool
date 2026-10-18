package com.cangshuo.toolbox.feature.subscription.domain

import com.cangshuo.toolbox.core.model.UnicodeText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow

enum class SubscriptionCategory(val storage: Int) {
    ENTERTAINMENT(0), SOFTWARE(1), MUSIC(2), CLOUD(3), EDUCATION(4), HEALTH(5), NEWS(6), OTHER(7);

    companion object {
        fun fromStorage(value: Int): SubscriptionCategory = entries.firstOrNull { it.storage == value } ?: OTHER
    }
}

enum class BillingCycle(val storage: Int) {
    WEEKLY(0), MONTHLY(1), QUARTERLY(2), HALF_YEARLY(3), YEARLY(4), CUSTOM(5);

    companion object {
        fun fromStorage(value: Int): BillingCycle = entries.firstOrNull { it.storage == value } ?: MONTHLY
    }
}

enum class SubscriptionFilter { ALL, ACTIVE, PAUSED, DUE }
enum class SubscriptionSort { UPDATED, NEXT_PAYMENT, PRICE, NAME }
enum class DueState { PAUSED, OVERDUE, DUE_SOON, NORMAL }
enum class SubscriptionError {
    NAME, PROVIDER, PAYMENT_METHOD, NOTES, PRICE, DATE, CUSTOM_DAYS, REMINDER_DAYS, LIMIT, STORAGE,
}

data class SubscriptionItem(
    val id: Long,
    val name: String,
    val provider: String,
    val category: SubscriptionCategory,
    val priceCents: Long,
    val billingCycle: BillingCycle,
    val customCycleDays: Int,
    val startEpochDay: Long,
    val billingAnchorEpochDay: Long,
    val nextPaymentEpochDay: Long,
    val paymentMethod: String,
    val notes: String,
    val reminderEnabled: Boolean,
    val reminderDays: Int,
    val autoRenew: Boolean,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val monthlyCostCents: Long
        get() = SubscriptionPolicy.monthlyCostCents(priceCents, billingCycle, customCycleDays)

    val yearlyCostCents: Long
        get() = SubscriptionPolicy.yearlyCostCents(priceCents, billingCycle, customCycleDays)

    fun dueState(todayEpochDay: Long): DueState = SubscriptionPolicy.dueState(this, todayEpochDay)

    fun isReminderDue(todayEpochDay: Long): Boolean =
        active && reminderEnabled &&
            SubscriptionPolicy.effectiveNextPayment(this, todayEpochDay) <= todayEpochDay + reminderDays
}

data class SubscriptionInput(
    val name: String,
    val provider: String,
    val category: SubscriptionCategory,
    val priceText: String,
    val billingCycle: BillingCycle,
    val customCycleDaysText: String,
    val startEpochDay: Long,
    val nextPaymentEpochDay: Long,
    val paymentMethod: String,
    val notes: String,
    val reminderEnabled: Boolean,
    val reminderDaysText: String,
    val autoRenew: Boolean,
    val active: Boolean,
)

data class SubscriptionStats(
    val totalCount: Int = 0,
    val activeCount: Int = 0,
    val pausedCount: Int = 0,
    val dueSoonCount: Int = 0,
    val overdueCount: Int = 0,
    val monthlyCostCents: Long = 0,
    val yearlyCostCents: Long = 0,
)

sealed interface SubscriptionValidation {
    data class Valid(val item: SubscriptionItem) : SubscriptionValidation
    data class Invalid(val error: SubscriptionError) : SubscriptionValidation
}

interface SubscriptionRepository {
    fun observeAll(): Flow<List<SubscriptionItem>>
    suspend fun find(id: Long): SubscriptionItem?
    suspend fun insert(item: SubscriptionItem): Long
    suspend fun update(item: SubscriptionItem)
    suspend fun delete(id: Long)
    fun nowMillis(): Long
    fun todayEpochDay(): Long
}

interface SubscriptionReminderScheduler {
    fun setEnabled(enabled: Boolean)
}

class SubscriptionLimitException : IllegalStateException("Subscription limit reached")

class SubscriptionUseCases(
    private val repository: SubscriptionRepository,
    private val reminderScheduler: SubscriptionReminderScheduler,
) {
    fun observeAll() = repository.observeAll()

    fun validate(existing: SubscriptionItem?, input: SubscriptionInput): SubscriptionValidation =
        SubscriptionPolicy.parseInput(existing, input, repository.nowMillis())

    suspend fun save(existing: SubscriptionItem?, input: SubscriptionInput): SubscriptionError? {
        val parsed = validate(existing, input)
        if (parsed is SubscriptionValidation.Invalid) return parsed.error
        val item = (parsed as SubscriptionValidation.Valid).item
        return try {
            if (existing == null) repository.insert(item) else repository.update(item)
            null
        } catch (_: SubscriptionLimitException) {
            SubscriptionError.LIMIT
        } catch (_: Exception) {
            SubscriptionError.STORAGE
        }
    }

    suspend fun setActive(item: SubscriptionItem, active: Boolean): SubscriptionError? =
        updateCopy(item.copy(active = active, updatedAt = repository.nowMillis()))

    suspend fun renew(item: SubscriptionItem): SubscriptionError? {
        val next = SubscriptionPolicy.nextFuturePayment(item, repository.todayEpochDay())
        return updateCopy(item.copy(nextPaymentEpochDay = next, updatedAt = repository.nowMillis()))
    }

    suspend fun delete(id: Long): SubscriptionError? = try {
        repository.delete(id)
        null
    } catch (_: Exception) {
        SubscriptionError.STORAGE
    }

    fun syncReminderSchedule(items: List<SubscriptionItem>) {
        reminderScheduler.setEnabled(items.any { it.active && it.reminderEnabled })
    }

    fun today() = repository.todayEpochDay()

    private suspend fun updateCopy(item: SubscriptionItem): SubscriptionError? = try {
        repository.update(item)
        null
    } catch (_: Exception) {
        SubscriptionError.STORAGE
    }
}

object SubscriptionPolicy {
    const val MAX_ITEMS = 500
    const val MAX_PRICE_CENTS = 99_999_999L
    const val MAX_NAME_CODE_POINTS = 80
    const val MAX_PROVIDER_CODE_POINTS = 80
    const val MAX_PAYMENT_METHOD_CODE_POINTS = 60
    const val MAX_NOTES_CODE_POINTS = 500
    const val MIN_REMINDER_DAYS = 0
    const val MAX_REMINDER_DAYS = 30
    const val MIN_CUSTOM_DAYS = 1
    const val MAX_CUSTOM_DAYS = 3_650
    val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
    val MAX_DATE: LocalDate = LocalDate.of(2200, 12, 31)
    private val pricePattern = Regex("\\d{1,6}(?:\\.\\d{1,2})?")
    private val digitsPattern = Regex("\\d{1,4}")

    fun parseInput(existing: SubscriptionItem?, input: SubscriptionInput, now: Long): SubscriptionValidation {
        val name = normalizeText(input.name)
        val provider = normalizeText(input.provider)
        val paymentMethod = normalizeText(input.paymentMethod)
        val notes = input.notes.trim()
        if (name.isBlank() || name.codePointCount(0, name.length) > MAX_NAME_CODE_POINTS) {
            return SubscriptionValidation.Invalid(SubscriptionError.NAME)
        }
        if (provider.isBlank() || provider.codePointCount(0, provider.length) > MAX_PROVIDER_CODE_POINTS) {
            return SubscriptionValidation.Invalid(SubscriptionError.PROVIDER)
        }
        if (paymentMethod.codePointCount(0, paymentMethod.length) > MAX_PAYMENT_METHOD_CODE_POINTS) {
            return SubscriptionValidation.Invalid(SubscriptionError.PAYMENT_METHOD)
        }
        if (notes.codePointCount(0, notes.length) > MAX_NOTES_CODE_POINTS) {
            return SubscriptionValidation.Invalid(SubscriptionError.NOTES)
        }
        val price = parsePriceCents(input.priceText)
            ?.takeIf { it > 0 } ?: return SubscriptionValidation.Invalid(SubscriptionError.PRICE)
        val customDays = if (input.billingCycle == BillingCycle.CUSTOM) {
            input.customCycleDaysText.trim().takeIf { digitsPattern.matches(it) }?.toIntOrNull()
                ?.takeIf { it in MIN_CUSTOM_DAYS..MAX_CUSTOM_DAYS }
                ?: return SubscriptionValidation.Invalid(SubscriptionError.CUSTOM_DAYS)
        } else {
            30
        }
        if (!validDate(input.startEpochDay) || !validDate(input.nextPaymentEpochDay) ||
            input.nextPaymentEpochDay < input.startEpochDay
        ) {
            return SubscriptionValidation.Invalid(SubscriptionError.DATE)
        }
        val reminderDays = input.reminderDaysText.trim().ifBlank { "7" }
            .takeIf { digitsPattern.matches(it) }?.toIntOrNull()
            ?.takeIf { it in MIN_REMINDER_DAYS..MAX_REMINDER_DAYS }
            ?: return SubscriptionValidation.Invalid(SubscriptionError.REMINDER_DAYS)
        val item = SubscriptionItem(
            id = existing?.id ?: 0,
            name = name,
            provider = provider,
            category = input.category,
            priceCents = price,
            billingCycle = input.billingCycle,
            customCycleDays = customDays,
            startEpochDay = input.startEpochDay,
            billingAnchorEpochDay = if (existing == null || input.nextPaymentEpochDay != existing.nextPaymentEpochDay) {
                input.nextPaymentEpochDay
            } else {
                existing.billingAnchorEpochDay
            },
            nextPaymentEpochDay = input.nextPaymentEpochDay,
            paymentMethod = paymentMethod,
            notes = notes,
            reminderEnabled = input.reminderEnabled,
            reminderDays = reminderDays,
            autoRenew = input.autoRenew,
            active = input.active,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return SubscriptionValidation.Valid(item)
    }

    fun parsePriceCents(text: String): Long? {
        val value = text.trim()
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

    fun monthlyCostCents(priceCents: Long, cycle: BillingCycle, customDays: Int): Long =
        BigDecimal.valueOf(yearlyCostCents(priceCents, cycle, customDays))
            .divide(BigDecimal(12), 0, RoundingMode.HALF_UP).longValueExact()

    fun yearlyCostCents(priceCents: Long, cycle: BillingCycle, customDays: Int): Long {
        val value = BigDecimal.valueOf(priceCents)
        return when (cycle) {
            BillingCycle.WEEKLY -> priceCents * 52
            BillingCycle.MONTHLY -> priceCents * 12
            BillingCycle.QUARTERLY -> priceCents * 4
            BillingCycle.HALF_YEARLY -> priceCents * 2
            BillingCycle.YEARLY -> priceCents
            BillingCycle.CUSTOM -> value.multiply(BigDecimal("365.2425"))
                .divide(BigDecimal(customDays.coerceAtLeast(1)), 0, RoundingMode.HALF_UP).longValueExact()
        }
    }

    fun advanceDate(date: LocalDate, cycle: BillingCycle, customDays: Int): LocalDate = when (cycle) {
        BillingCycle.WEEKLY -> date.plusWeeks(1)
        BillingCycle.MONTHLY -> date.plusMonths(1)
        BillingCycle.QUARTERLY -> date.plusMonths(3)
        BillingCycle.HALF_YEARLY -> date.plusMonths(6)
        BillingCycle.YEARLY -> date.plusYears(1)
        BillingCycle.CUSTOM -> date.plusDays(customDays.coerceAtLeast(1).toLong())
    }

    fun nextFuturePayment(item: SubscriptionItem, todayEpochDay: Long): Long {
        val target = maxOf(todayEpochDay, item.nextPaymentEpochDay) + 1L
        return firstCandidateOnOrAfter(item.billingAnchorEpochDay, item, target)
    }

    fun dueState(item: SubscriptionItem, todayEpochDay: Long): DueState {
        if (!item.active) return DueState.PAUSED
        val next = effectiveNextPayment(item, todayEpochDay)
        return when {
            next < todayEpochDay -> DueState.OVERDUE
            next <= todayEpochDay + 7L -> DueState.DUE_SOON
            else -> DueState.NORMAL
        }
    }

    fun effectiveNextPayment(item: SubscriptionItem, todayEpochDay: Long): Long {
        if (!item.active || !item.autoRenew || item.nextPaymentEpochDay >= todayEpochDay) {
            return item.nextPaymentEpochDay
        }
        return firstCandidateOnOrAfter(item.billingAnchorEpochDay, item, todayEpochDay)
    }

    fun filterAndSort(
        items: List<SubscriptionItem>,
        filter: SubscriptionFilter,
        sort: SubscriptionSort,
        query: String,
        todayEpochDay: Long,
    ): List<SubscriptionItem> {
        val needle = query.trim().lowercase(Locale.ROOT)
        val visible = items.filter { item ->
            val filterMatches = when (filter) {
                SubscriptionFilter.ALL -> true
                SubscriptionFilter.ACTIVE -> item.active
                SubscriptionFilter.PAUSED -> !item.active
                SubscriptionFilter.DUE -> item.active &&
                    effectiveNextPayment(item, todayEpochDay) <= todayEpochDay + 7L
            }
            filterMatches && (needle.isEmpty() ||
                item.name.lowercase(Locale.ROOT).contains(needle) ||
                item.provider.lowercase(Locale.ROOT).contains(needle) ||
                item.paymentMethod.lowercase(Locale.ROOT).contains(needle) ||
                item.notes.lowercase(Locale.ROOT).contains(needle))
        }
        val name = compareBy<SubscriptionItem> { it.name.lowercase(Locale.ROOT) }
        return when (sort) {
            SubscriptionSort.UPDATED -> visible.sortedWith(
                compareByDescending<SubscriptionItem> { it.updatedAt }.thenByDescending { it.id },
            )
            SubscriptionSort.NEXT_PAYMENT -> visible.sortedWith(
                compareBy<SubscriptionItem> { if (it.active) 0 else 1 }
                    .thenBy { effectiveNextPayment(it, todayEpochDay) }.then(name),
            )
            SubscriptionSort.PRICE -> visible.sortedWith(compareByDescending<SubscriptionItem> { it.priceCents }.then(name))
            SubscriptionSort.NAME -> visible.sortedWith(name)
        }
    }

    fun stats(items: List<SubscriptionItem>, todayEpochDay: Long): SubscriptionStats {
        val active = items.filter { it.active }
        return SubscriptionStats(
            totalCount = items.size,
            activeCount = active.size,
            pausedCount = items.count { !it.active },
            dueSoonCount = active.count {
                effectiveNextPayment(it, todayEpochDay) in todayEpochDay..(todayEpochDay + 7L)
            },
            overdueCount = active.count { effectiveNextPayment(it, todayEpochDay) < todayEpochDay },
            monthlyCostCents = active.sumOf { it.monthlyCostCents },
            yearlyCostCents = active.sumOf { it.yearlyCostCents },
        )
    }

    fun normalizeQuery(query: String): String =
        UnicodeText.takeCodePointsWithoutSplittingCluster(query, MAX_NAME_CODE_POINTS)

    private fun normalizeText(text: String) = text.trim().replace(Regex("\\s+"), " ")

    private fun validDate(epochDay: Long): Boolean {
        val date = try {
            LocalDate.ofEpochDay(epochDay)
        } catch (_: DateTimeException) {
            return false
        }
        return !date.isBefore(MIN_DATE) && !date.isAfter(MAX_DATE)
    }

    private fun firstCandidateOnOrAfter(anchorEpochDay: Long, item: SubscriptionItem, targetEpochDay: Long): Long {
        if (anchorEpochDay >= targetEpochDay) return anchorEpochDay
        val anchor = LocalDate.ofEpochDay(anchorEpochDay)
        val target = LocalDate.ofEpochDay(targetEpochDay)
        var cycles = when (item.billingCycle) {
            BillingCycle.WEEKLY -> ceilDiv(java.time.temporal.ChronoUnit.DAYS.between(anchor, target), 7L)
            BillingCycle.CUSTOM -> ceilDiv(
                java.time.temporal.ChronoUnit.DAYS.between(anchor, target),
                item.customCycleDays.coerceAtLeast(1).toLong(),
            )
            BillingCycle.MONTHLY -> ceilDiv(java.time.temporal.ChronoUnit.MONTHS.between(anchor, target), 1L)
            BillingCycle.QUARTERLY -> ceilDiv(java.time.temporal.ChronoUnit.MONTHS.between(anchor, target), 3L)
            BillingCycle.HALF_YEARLY -> ceilDiv(java.time.temporal.ChronoUnit.MONTHS.between(anchor, target), 6L)
            BillingCycle.YEARLY -> ceilDiv(java.time.temporal.ChronoUnit.MONTHS.between(anchor, target), 12L)
        }.coerceAtLeast(0L).toInt()
        var candidate = candidateAfter(item, anchorEpochDay, cycles)
        while (candidate < targetEpochDay) {
            cycles++
            candidate = candidateAfter(item, anchorEpochDay, cycles)
        }
        return candidate
    }

    private fun ceilDiv(value: Long, divisor: Long): Long =
        if (value <= 0L) 0L else (value + divisor - 1L) / divisor

    private fun candidateAfter(item: SubscriptionItem, startEpochDay: Long, cycles: Int): Long {
        val base = LocalDate.ofEpochDay(startEpochDay)
        val date = when (item.billingCycle) {
            BillingCycle.WEEKLY -> base.plusWeeks(cycles.toLong())
            BillingCycle.MONTHLY -> base.plusMonths(cycles.toLong())
            BillingCycle.QUARTERLY -> base.plusMonths(3L * cycles)
            BillingCycle.HALF_YEARLY -> base.plusMonths(6L * cycles)
            BillingCycle.YEARLY -> base.plusYears(cycles.toLong())
            BillingCycle.CUSTOM -> base.plusDays(item.customCycleDays.coerceAtLeast(1).toLong() * cycles)
        }
        return date.toEpochDay()
    }
}
