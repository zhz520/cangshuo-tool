package com.cangshuo.toolbox.feature.anniversary.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryCategory
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryError
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryEvent
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryFilter
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryLimitException
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryLunarInfo
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryPolicy
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryRepeat
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversarySort
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryUseCases
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AnniversaryViewModel(
    private val useCases: AnniversaryUseCases,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var allEvents = emptyList<AnniversaryEvent>()
    private var today = useCases.today()
    private val mutable = MutableStateFlow(
        AnniversaryUiState(
            filter = saved.get<String>("anniversaryFilter")?.let { value -> AnniversaryFilter.entries.firstOrNull { it.name == value } }
                ?: AnniversaryFilter.ALL,
            sort = saved.get<String>("anniversarySort")?.let { value -> AnniversarySort.entries.firstOrNull { it.name == value } }
                ?: AnniversarySort.PINNED,
            query = saved.get<String>("anniversaryQuery")?.take(80).orEmpty(),
            todayEpochDay = today,
        ),
    )
    val state: StateFlow<AnniversaryUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                useCases.observe().collect { events ->
                    allEvents = events
                    publish(loading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false, message = AnniversaryMessage.STORAGE_FAILED)
            }
        }
    }

    fun refreshToday() {
        val refreshed = useCases.today()
        if (refreshed == today) return
        today = refreshed
        publish(mutable.value.loading)
    }

    fun setFilter(filter: AnniversaryFilter) {
        saved["anniversaryFilter"] = filter.name
        mutable.value = mutable.value.copy(filter = filter)
        publish(mutable.value.loading)
    }

    fun setSort(sort: AnniversarySort) {
        saved["anniversarySort"] = sort.name
        mutable.value = mutable.value.copy(sort = sort)
        publish(mutable.value.loading)
    }

    fun setQuery(query: String) {
        val normalized = query.take(80)
        saved["anniversaryQuery"] = normalized
        mutable.value = mutable.value.copy(query = normalized)
        publish(mutable.value.loading)
    }

    fun save(
        id: Long?,
        title: String,
        notes: String,
        dateEpochDay: Long,
        repeat: AnniversaryRepeat,
        category: AnniversaryCategory,
        pinned: Boolean,
    ): Boolean {
        if (mutable.value.saving) return false
        val error = useCases.validate(title, notes, dateEpochDay)
        if (error != null) {
            mutable.value = mutable.value.copy(editError = error)
            return false
        }
        if (id == null && allEvents.size >= AnniversaryPolicy.MAX_EVENTS) {
            mutable.value = mutable.value.copy(editError = AnniversaryError.LIMIT)
            return false
        }
        val current = id?.let { eventId -> allEvents.firstOrNull { it.id == eventId } }
        if (id != null && current == null) return false
        mutable.value = mutable.value.copy(saving = true, editError = null)
        viewModelScope.launch {
            try {
                if (current == null) {
                    useCases.insert(useCases.create(title, notes, dateEpochDay, repeat, category, pinned))
                    mutable.value = mutable.value.copy(
                        saving = false,
                        saveSuccessRevision = mutable.value.saveSuccessRevision + 1,
                        message = AnniversaryMessage.ADDED,
                    )
                } else {
                    useCases.update(useCases.update(current, title, notes, dateEpochDay, repeat, category, pinned))
                    mutable.value = mutable.value.copy(
                        saving = false,
                        saveSuccessRevision = mutable.value.saveSuccessRevision + 1,
                        message = AnniversaryMessage.UPDATED,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AnniversaryLimitException) {
                mutable.value = mutable.value.copy(saving = false, editError = AnniversaryError.LIMIT)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(saving = false, editError = AnniversaryError.STORAGE)
            }
        }
        return true
    }

    fun setPinned(event: AnniversaryEvent, pinned: Boolean) {
        if (event.id in mutable.value.busyEventIds) return
        setBusy(event.id, true)
        viewModelScope.launch {
            try {
                useCases.setPinned(event.id, pinned)
                mutable.value = mutable.value.copy(message = if (pinned) AnniversaryMessage.PINNED else AnniversaryMessage.UNPINNED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = AnniversaryMessage.STORAGE_FAILED)
            } finally {
                setBusy(event.id, false)
            }
        }
    }

    fun delete(event: AnniversaryEvent) {
        viewModelScope.launch {
            try {
                useCases.delete(event.id)
                mutable.value = mutable.value.copy(message = AnniversaryMessage.DELETED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = AnniversaryMessage.STORAGE_FAILED)
            }
        }
    }

    fun lunarInfo(date: LocalDate): AnniversaryLunarInfo? = try {
        useCases.lunarInfo(date)
    } catch (_: Exception) {
        null
    }

    fun clearEditError() {
        if (mutable.value.editError != null) mutable.value = mutable.value.copy(editError = null)
    }

    fun consumeMessage() {
        if (mutable.value.message != null) mutable.value = mutable.value.copy(message = null)
    }

    private fun setBusy(id: Long, busy: Boolean) {
        val ids = if (busy) mutable.value.busyEventIds + id else mutable.value.busyEventIds - id
        mutable.value = mutable.value.copy(busyEventIds = ids)
    }

    private fun publish(loading: Boolean) {
        val current = mutable.value
        mutable.value = current.copy(
            items = AnniversaryPolicy.filterAndSort(allEvents, current.filter, current.sort, current.query, today),
            stats = AnniversaryPolicy.stats(allEvents, today),
            todayEpochDay = today,
            loading = loading,
        )
    }

    companion object {
        fun factory(useCases: AnniversaryUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                AnniversaryViewModel(useCases, extras.createSavedStateHandle()) as T
        }
    }
}
