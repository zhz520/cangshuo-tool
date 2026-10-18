package com.cangshuo.toolbox.feature.inventory.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.inventory.domain.InventoryError
import com.cangshuo.toolbox.feature.inventory.domain.InventoryFilter
import com.cangshuo.toolbox.feature.inventory.domain.InventoryInput
import com.cangshuo.toolbox.feature.inventory.domain.InventoryItem
import com.cangshuo.toolbox.feature.inventory.domain.InventoryPolicy
import com.cangshuo.toolbox.feature.inventory.domain.InventorySort
import com.cangshuo.toolbox.feature.inventory.domain.InventoryUseCases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val useCases: InventoryUseCases,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var allItems = emptyList<InventoryItem>()
    private var today = useCases.today()
    private val mutable = MutableStateFlow(
        InventoryUiState(
            filter = saved.get<String>("inventoryFilter")?.let { value ->
                InventoryFilter.entries.firstOrNull { it.name == value }
            } ?: InventoryFilter.ALL,
            sort = saved.get<String>("inventorySort")?.let { value ->
                InventorySort.entries.firstOrNull { it.name == value }
            } ?: InventorySort.UPDATED,
            query = InventoryPolicy.normalizeQuery(saved.get<String>("inventoryQuery").orEmpty()),
            todayEpochDay = today,
        ),
    )
    val state: StateFlow<InventoryUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                useCases.observeAll().collect { items ->
                    allItems = items
                    publish(loading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false)
                emitMessage(InventoryMessage.STORAGE_FAILED)
            }
        }
    }

    fun refreshToday() {
        val next = useCases.today()
        if (next == today) return
        today = next
        publish(mutable.value.loading)
    }

    fun setFilter(filter: InventoryFilter) {
        saved["inventoryFilter"] = filter.name
        mutable.value = mutable.value.copy(filter = filter)
        publish(mutable.value.loading)
    }

    fun setSort(sort: InventorySort) {
        saved["inventorySort"] = sort.name
        mutable.value = mutable.value.copy(sort = sort)
        publish(mutable.value.loading)
    }

    fun setQuery(query: String) {
        val normalized = InventoryPolicy.normalizeQuery(query)
        saved["inventoryQuery"] = normalized
        mutable.value = mutable.value.copy(query = normalized)
        publish(mutable.value.loading)
    }

    fun save(id: Long?, input: InventoryInput): Boolean {
        if (mutable.value.saving) return false
        val current = id?.let { eventId -> allItems.firstOrNull { it.id == eventId } }
        if (id != null && current == null) return false
        if (id == null && allItems.size >= InventoryPolicy.MAX_ITEMS) {
            mutable.value = mutable.value.copy(editError = InventoryError.LIMIT)
            return false
        }
        val validation = useCases.validate(current, input)
        if (validation is com.cangshuo.toolbox.feature.inventory.domain.InventoryValidation.Invalid) {
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
                emitMessage(if (current == null) InventoryMessage.ADDED else InventoryMessage.UPDATED)
            } else {
                mutable.value = mutable.value.copy(saving = false, editError = error)
            }
        }
        return true
    }

    fun adjust(item: InventoryItem, delta: Int) {
        if (item.id in mutable.value.busyItemIds) return
        setBusy(item.id, true)
        viewModelScope.launch {
            try {
                val error = useCases.adjust(item.id, delta)
                if (error != null) emitMessage(InventoryMessage.STORAGE_FAILED)
            } finally {
                setBusy(item.id, false)
            }
        }
    }

    fun delete(item: InventoryItem) {
        if (item.id in mutable.value.busyItemIds) return
        setBusy(item.id, true)
        viewModelScope.launch {
            try {
                val error = useCases.delete(item.id)
                emitMessage(if (error == null) InventoryMessage.DELETED else InventoryMessage.STORAGE_FAILED)
            } finally {
                setBusy(item.id, false)
            }
        }
    }

    fun clearEditError() {
        if (mutable.value.editError != null) mutable.value = mutable.value.copy(editError = null)
    }

    fun consumeMessage() {
        if (mutable.value.message != null) mutable.value = mutable.value.copy(message = null)
    }

    private fun publish(loading: Boolean) {
        val current = mutable.value
        mutable.value = current.copy(
            items = InventoryPolicy.filterAndSort(allItems, current.filter, current.sort, current.query, today),
            stats = InventoryPolicy.stats(allItems, today),
            todayEpochDay = today,
            loading = loading,
        )
    }

    private fun setBusy(id: Long, busy: Boolean) {
        val ids = if (busy) mutable.value.busyItemIds + id else mutable.value.busyItemIds - id
        mutable.value = mutable.value.copy(busyItemIds = ids)
    }

    private fun emitMessage(message: InventoryMessage) {
        mutable.value = mutable.value.copy(
            message = message,
            messageRevision = mutable.value.messageRevision + 1,
        )
    }

    companion object {
        fun factory(useCases: InventoryUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                InventoryViewModel(useCases, extras.createSavedStateHandle()) as T
        }
    }
}
