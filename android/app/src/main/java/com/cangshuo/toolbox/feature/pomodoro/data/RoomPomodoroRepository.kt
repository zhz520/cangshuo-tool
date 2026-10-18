package com.cangshuo.toolbox.feature.pomodoro.data

import android.content.Context
import com.cangshuo.toolbox.core.database.PomodoroSessionDao
import com.cangshuo.toolbox.core.database.PomodoroSessionEntity
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroAdvance
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPhase
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPolicy
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroRepository
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSession
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSettings
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroTimerState
import com.cangshuo.toolbox.feature.pomodoro.service.PomodoroTimerService
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomPomodoroRepository(
    private val context: Context,
    private val dao: PomodoroSessionDao,
) : PomodoroRepository {
    private val store = PomodoroTimerStore(context.applicationContext)
    private val mutex = Mutex()

    override fun observeTimer(): StateFlow<PomodoroTimerState> = store.timer
    override fun observeSettings(): StateFlow<PomodoroSettings> = store.settings

    override fun observeHistory(): Flow<List<PomodoroSession>> =
        dao.observeRecent().map { rows -> rows.map(::toDomain) }

    override suspend fun tick(): PomodoroAdvance = mutex.withLock {
        val advance = PomodoroPolicy.tick(store.timer.value, store.settings.value, nowMillis())
        apply(advance)
    }

    override suspend fun start(): PomodoroTimerState = mutex.withLock {
        val current = settleForAction()
        val started = PomodoroPolicy.start(current, store.settings.value, nowMillis())
        store.saveTimer(started)
        if (started.running) PomodoroTimerService.start(context)
        started
    }

    override suspend fun pause(): PomodoroTimerState = mutex.withLock {
        val current = settleForAction()
        val paused = PomodoroPolicy.pause(current, store.settings.value, nowMillis())
        store.saveTimer(paused)
        paused
    }

    override suspend fun skip(): PomodoroAdvance = mutex.withLock {
        val advance = PomodoroPolicy.skip(store.timer.value, store.settings.value, nowMillis())
        apply(advance, startServiceIfRunning = true)
    }

    override suspend fun reset(): PomodoroAdvance = mutex.withLock {
        val advance = PomodoroPolicy.reset(store.timer.value, store.settings.value, nowMillis())
        apply(advance, startServiceIfRunning = true)
    }

    override suspend fun switchPhase(phase: PomodoroPhase): PomodoroTimerState = mutex.withLock {
        val current = settleForAction()
        val switched = PomodoroPolicy.switchPhase(current, store.settings.value, phase)
        store.saveTimer(switched)
        switched
    }

    override suspend fun setLabel(label: String): PomodoroTimerState = mutex.withLock {
        val error = PomodoroPolicy.validateLabel(label)
        require(error == null) { "Pomodoro label is too long" }
        val updated = store.timer.value.copy(label = PomodoroPolicy.normalizeLabel(label))
        store.saveTimer(updated)
        updated
    }

    override suspend fun updateSettings(settings: PomodoroSettings): PomodoroSettings = mutex.withLock {
        val current = store.timer.value
        require(!current.running) { "Pomodoro settings cannot change while the timer is running" }
        store.saveSettings(settings)
        store.settings.value
    }

    override suspend fun clearHistory(): Int = mutex.withLock { dao.clear() }

    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    private suspend fun settleForAction(): PomodoroTimerState {
        val advance = PomodoroPolicy.tick(store.timer.value, store.settings.value, nowMillis())
        apply(advance)
        return advance.state
    }

    private suspend fun apply(
        advance: PomodoroAdvance,
        startServiceIfRunning: Boolean = false,
    ): PomodoroAdvance {
        val persisted = advance.records.map { session ->
            val id = dao.insertAndTrim(toEntity(session), PomodoroPolicy.MAX_HISTORY)
            session.copy(id = id)
        }
        store.saveTimer(advance.state)
        if (startServiceIfRunning && advance.state.running) PomodoroTimerService.start(context)
        return advance.copy(records = persisted)
    }

    private fun toDomain(row: PomodoroSessionEntity) = PomodoroSession(
        id = row.id,
        label = row.label,
        phase = PomodoroPhase.fromStorage(row.phase),
        plannedSeconds = row.plannedSeconds,
        elapsedSeconds = row.elapsedSeconds,
        completed = row.completed,
        startedAt = row.startedAt,
        endedAt = row.endedAt,
    )

    private fun toEntity(session: PomodoroSession) = PomodoroSessionEntity(
        id = session.id,
        label = session.label,
        phase = session.phase.storage,
        plannedSeconds = session.plannedSeconds,
        elapsedSeconds = session.elapsedSeconds,
        completed = session.completed,
        startedAt = session.startedAt,
        endedAt = session.endedAt,
    )
}
