package com.cangshuo.toolbox.feature.subscription.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionError
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionFilter
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionInput
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionItem
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionPolicy
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionSort
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionUseCases
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SubscriptionViewModel(
    private val useCases: SubscriptionUseCases,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var allItems = emptyList<SubscriptionItem>()
    private var today = useCases.today()
    private var reminderScheduleEnabled: Boolean? = null
    private val mutable = MutableStateFlow(
        SubscriptionUiState(
            filter = saved.get<String>("subscriptionFilter")?.let { value ->
                SubscriptionFilter.entries.firstOrNull { it.name == value }
            } ?: SubscriptionFilter.ALL,
            sort = saved.get<String>("subscriptionSort")?.let { value ->
                SubscriptionSort.entries.firstOrNull { it.name == value }
            } ?: SubscriptionSort.NEXT_PAYMENT,
            query = SubscriptionPolicy.normalizeQuery(saved.get<String>("subscriptionQuery").orEmpty()),
            todayEpochDay = today,
        ),
    )
    val state: StateFlow<SubscriptionUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            var failureReported = false
            while (true) {
                try {
                    useCases.observeAll().collect { items ->
                        allItems = items
                        syncReminderSchedule()
                        publish(loading = false)
                        failureReported = false
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutable.value = mutable.value.copy(loading = false)
                    if (!failureReported) {
                        emitMessage(SubscriptionMessage.STORAGE_FAILED)
                        failureReported = true
                    }
                    delay(5_000L)
                }
            }
        }
    }

    fun refreshToday() {
        val next = useCases.today()
        if (next == today) return
        today = next
        publish(mutable.value.loading)
    }

    fun setFilter(filter: SubscriptionFilter) {
        saved["subscriptionFilter"] = filter.name
        mutable.value = mutable.value.copy(filter = filter)
        publish(mutable.value.loading)
    }

    fun setSort(sort: SubscriptionSort) {
        saved["subscriptionSort"] = sort.name
        mutable.value = mutable.value.copy(sort = sort)
        publish(mutable.value.loading)
    }

    fun setQuery(query: String) {
        val normalized = SubscriptionPolicy.normalizeQuery(query)
        saved["subscriptionQuery"] = normalized
        mutable.value = mutable.value.copy(query = normalized)
        publish(mutable.value.loading)
    }

    fun save(id: Long?, input: SubscriptionInput): Boolean {
        if (mutable.value.saving) return false
        val current = id?.let { itemId -> allItems.firstOrNull { it.id == itemId } }
        if (id != null && current == null) return false
        if (id == null && allItems.size >= SubscriptionPolicy.MAX_ITEMS) {
            mutable.value = mutable.value.copy(editError = SubscriptionError.LIMIT)
            return false
        }
        val validation = useCases.validate(current, input)
        if (validation is SubscriptionValidation.Invalid) {
            mutable.value = mutable.value.copy(editError = validation.error)
            return false
        }
        mutable.value = mutable.value.copy(saving = true, editError = null)
        viewModelScope.launch {
            val error = useCases.save(current, input)
            if (error == null) {
                mutable.value = mutable.value.copy(
                    saving = false,
                    saveSuccessRevision = mutable.value.saveSuccessRevision + 1,
                )
                emitMessage(if (current == null) SubscriptionMessage.ADDED else SubscriptionMessage.UPDATED)
            } else {
                mutable.value = mutable.value.copy(saving = false, editError = error)
            }
        }
        return true
    }

    fun setActive(item: SubscriptionItem, active: Boolean) = withBusy(item.id) {
        val error = useCases.setActive(item, active)
        emitMessage(
            if (error == null) {
                if (active) SubscriptionMessage.RESUMED else SubscriptionMessage.PAUSED
            } else {
                SubscriptionMessage.STORAGE_FAILED
            },
        )
    }

    fun renew(item: SubscriptionItem) = withBusy(item.id) {
        val error = useCases.renew(item)
        emitMessage(if (error == null) SubscriptionMessage.RENEWED else SubscriptionMessage.STORAGE_FAILED)
    }

    fun delete(item: SubscriptionItem) = withBusy(item.id) {
        val error = useCases.delete(item.id)
        emitMessage(if (error == null) SubscriptionMessage.DELETED else SubscriptionMessage.STORAGE_FAILED)
    }

    fun clearEditError() {
        if (mutable.value.editError != null) mutable.value = mutable.value.copy(editError = null)
    }

    fun consumeMessage() {
        if (mutable.value.message != null) mutable.value = mutable.value.copy(message = null)
    }

    private fun withBusy(id: Long, operation: suspend () -> Unit) {
        if (id in mutable.value.busyItemIds) return
        setBusy(id, true)
        viewModelScope.launch {
            try {
                operation()
            } finally {
                setBusy(id, false)
            }
        }
    }

    private fun syncReminderSchedule() {
        val enabled = allItems.any { it.active && it.reminderEnabled }
        if (reminderScheduleEnabled == enabled) return
        useCases.syncReminderSchedule(allItems)
        reminderScheduleEnabled = enabled
    }

    private fun publish(loading: Boolean) {
        val current = mutable.value
        mutable.value = current.copy(
            items = SubscriptionPolicy.filterAndSort(allItems, current.filter, current.sort, current.query, today),
            stats = SubscriptionPolicy.stats(allItems, today),
            todayEpochDay = today,
            hasActiveReminders = allItems.any { it.active && it.reminderEnabled },
            loading = loading,
        )
    }

    private fun setBusy(id: Long, busy: Boolean) {
        val ids = if (busy) mutable.value.busyItemIds + id else mutable.value.busyItemIds - id
        mutable.value = mutable.value.copy(busyItemIds = ids)
    }

    private fun emitMessage(message: SubscriptionMessage) {
        mutable.value = mutable.value.copy(
            message = message,
            messageRevision = mutable.value.messageRevision + 1,
        )
    }

    companion object {
        fun factory(useCases: SubscriptionUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                SubscriptionViewModel(useCases, extras.createSavedStateHandle()) as T
        }
    }
}
