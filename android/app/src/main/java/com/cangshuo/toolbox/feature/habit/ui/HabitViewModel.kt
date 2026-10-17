package com.cangshuo.toolbox.feature.habit.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.habit.domain.Habit
import com.cangshuo.toolbox.feature.habit.domain.HabitError
import com.cangshuo.toolbox.feature.habit.domain.HabitFilter
import com.cangshuo.toolbox.feature.habit.domain.HabitPolicy
import com.cangshuo.toolbox.feature.habit.domain.HabitLimitException
import com.cangshuo.toolbox.feature.habit.domain.HabitSort
import com.cangshuo.toolbox.feature.habit.domain.HabitUseCases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HabitViewModel(
    private val useCases: HabitUseCases,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var allHabits = emptyList<Habit>()
    private var today = useCases.today()
    private val mutable = MutableStateFlow(
        HabitUiState(
            filter = saved.get<String>("habitFilter")?.let { value -> HabitFilter.entries.firstOrNull { it.name == value } }
                ?: HabitFilter.ALL,
            sort = saved.get<String>("habitSort")?.let { value -> HabitSort.entries.firstOrNull { it.name == value } }
                ?: HabitSort.TODAY_FIRST,
            query = saved.get<String>("habitQuery")?.take(80).orEmpty(),
            todayEpochDay = today,
        ),
    )
    val state: StateFlow<HabitUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                useCases.observe().collect { habits ->
                    allHabits = habits
                    publish(loading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false, message = HabitMessage.STORAGE_FAILED)
            }
        }
    }

    fun refreshToday() {
        val refreshed = useCases.today()
        if (refreshed == today) return
        today = refreshed
        publish(mutable.value.loading)
    }

    fun setFilter(filter: HabitFilter) {
        saved["habitFilter"] = filter.name
        mutable.value = mutable.value.copy(filter = filter)
        publish(mutable.value.loading)
    }

    fun setSort(sort: HabitSort) {
        saved["habitSort"] = sort.name
        mutable.value = mutable.value.copy(sort = sort)
        publish(mutable.value.loading)
    }

    fun setQuery(query: String) {
        val normalized = query.take(80)
        saved["habitQuery"] = normalized
        mutable.value = mutable.value.copy(query = normalized)
        publish(mutable.value.loading)
    }

    fun save(id: Long?, name: String, description: String, weekdaysMask: Int): Boolean {
        val error = useCases.validate(name, description, weekdaysMask)
        if (error != null) {
            mutable.value = mutable.value.copy(editError = error)
            return false
        }
        if (id == null && allHabits.size >= HabitPolicy.MAX_HABITS) {
            mutable.value = mutable.value.copy(editError = HabitError.LIMIT)
            return false
        }
        val current = id?.let { habitId -> allHabits.firstOrNull { it.id == habitId } }
        if (id != null && current == null) return false
        if (current?.archived == true) {
            mutable.value = mutable.value.copy(editError = HabitError.ARCHIVED)
            return false
        }
        mutable.value = mutable.value.copy(saving = true, editError = null)
        viewModelScope.launch {
            try {
                if (current == null) {
                    useCases.insert(useCases.create(name, description, weekdaysMask))
                    mutable.value = mutable.value.copy(
                        saving = false,
                        editError = null,
                        saveSuccessRevision = mutable.value.saveSuccessRevision + 1,
                        message = HabitMessage.CREATED,
                    )
                } else {
                    useCases.update(useCases.update(current, name, description, weekdaysMask))
                    mutable.value = mutable.value.copy(
                        saving = false,
                        editError = null,
                        saveSuccessRevision = mutable.value.saveSuccessRevision + 1,
                        message = HabitMessage.UPDATED,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: HabitLimitException) {
                mutable.value = mutable.value.copy(saving = false, editError = HabitError.LIMIT)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(saving = false, editError = HabitError.STORAGE)
            }
        }
        return true
    }

    fun toggleDate(habit: Habit, dateEpochDay: Long) {
        if (habit.id in mutable.value.busyHabitIds || !HabitPolicy.canToggleDate(habit, dateEpochDay, today)) return
        val checked = HabitPolicy.isChecked(habit, dateEpochDay)
        setBusy(habit.id, true)
        viewModelScope.launch {
            try {
                if (checked) {
                    useCases.removeEntry(habit.id, dateEpochDay)
                    mutable.value = mutable.value.copy(message = HabitMessage.UNCHECKED)
                } else if (useCases.addEntry(habit.id, dateEpochDay)) {
                    mutable.value = mutable.value.copy(message = HabitMessage.CHECKED)
                } else {
                    mutable.value = mutable.value.copy(message = HabitMessage.ENTRY_LIMIT)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = HabitMessage.STORAGE_FAILED)
            } finally {
                setBusy(habit.id, false)
            }
        }
    }

    fun setArchived(habit: Habit, archived: Boolean) {
        if (habit.id in mutable.value.busyHabitIds) return
        setBusy(habit.id, true)
        viewModelScope.launch {
            try {
                useCases.setArchived(habit.id, archived)
                val restoredFilter = if (!archived && mutable.value.filter == HabitFilter.ARCHIVED) HabitFilter.ALL else mutable.value.filter
                saved["habitFilter"] = restoredFilter.name
                mutable.value = mutable.value.copy(
                    filter = restoredFilter,
                    message = if (archived) HabitMessage.ARCHIVED else HabitMessage.RESTORED,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = HabitMessage.STORAGE_FAILED)
            } finally {
                setBusy(habit.id, false)
            }
        }
    }

    fun delete(habit: Habit) {
        viewModelScope.launch {
            try {
                useCases.delete(habit.id)
                mutable.value = mutable.value.copy(message = HabitMessage.DELETED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = HabitMessage.STORAGE_FAILED)
            }
        }
    }

    fun clearHistory(habit: Habit) {
        viewModelScope.launch {
            try {
                if (useCases.clearEntries(habit.id) >= 0) mutable.value = mutable.value.copy(message = HabitMessage.HISTORY_CLEARED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = HabitMessage.STORAGE_FAILED)
            }
        }
    }

    fun clearEditError() {
        if (mutable.value.editError != null) mutable.value = mutable.value.copy(editError = null)
    }

    fun consumeMessage() {
        if (mutable.value.message != null) mutable.value = mutable.value.copy(message = null)
    }

    private fun setBusy(id: Long, busy: Boolean) {
        val ids = if (busy) mutable.value.busyHabitIds + id else mutable.value.busyHabitIds - id
        mutable.value = mutable.value.copy(busyHabitIds = ids)
    }

    private fun publish(loading: Boolean) {
        val current = mutable.value
        val visible = HabitPolicy.filterAndSort(allHabits, current.filter, current.sort, current.query, today)
        mutable.value = current.copy(
            items = visible,
            stats = HabitPolicy.stats(allHabits, today),
            todayEpochDay = today,
            loading = loading,
        )
    }

    companion object {
        fun factory(useCases: HabitUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                HabitViewModel(useCases, extras.createSavedStateHandle()) as T
        }
    }
}
