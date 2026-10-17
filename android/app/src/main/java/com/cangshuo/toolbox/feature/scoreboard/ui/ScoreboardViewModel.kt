package com.cangshuo.toolbox.feature.scoreboard.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardCore
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardPolicy
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardSession
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardUseCases
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreSide
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScoreboardViewModel(
    private val useCases: ScoreboardUseCases,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var session: ScoreboardSession = restoreSession()
    private val now = useCases.clock()
    private val mutable = MutableStateFlow(
        ScoreboardUiState(
            session = session,
            displayMs = session.elapsedMs(now.monotonicMs),
            keepScreenOn = saved.get<Boolean>("sbKeepScreen") ?: true,
        ),
    )
    val state: StateFlow<ScoreboardUiState> = mutable.asStateFlow()

    private var foreground = false
    private var ticker: Job? = null

    fun start() {
        foreground = true
        refresh()
        runTicker()
    }

    fun stop() {
        foreground = false
        ticker?.cancel()
        ticker = null
        refresh()
    }

    fun increment(side: ScoreSide) = changeScore(side, session.step)

    fun decrement(side: ScoreSide) = changeScore(side, -session.step)

    fun setScore(side: ScoreSide, score: Int) {
        val now = useCases.clock().epochMs
        session = useCases.setScore(session, side, score, now)
        persist()
    }

    fun swap() {
        val now = useCases.clock().epochMs
        session = useCases.swap(session, now)
        persist()
    }

    fun nextPeriod() {
        val now = useCases.clock().epochMs
        session = useCases.nextPeriod(session, now)
        persist()
    }

    fun editNames(leftName: String, rightName: String) {
        val now = useCases.clock().epochMs
        session = useCases.editNames(session, leftName, rightName, now)
        persist()
    }

    fun undo() {
        session = useCases.undo(session)
        persist()
    }

    fun reset() {
        session = useCases.reset(session)
        persist()
        runTicker()
    }

    fun toggleTimer() {
        val now = useCases.clock().monotonicMs
        session = if (session.running) {
            ScoreboardPolicy.pauseTimer(session, now)
        } else {
            ScoreboardPolicy.startTimer(session, now)
        }
        persist()
        runTicker()
    }

    fun resetTimer() {
        session = ScoreboardPolicy.resetTimer(session, useCases.clock().monotonicMs)
        persist()
        runTicker()
    }

    fun setStep(step: Int) {
        session = session.copy(step = ScoreboardPolicy.normalizeStep(step))
        persist()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        saved["sbKeepScreen"] = enabled
        mutable.update { it.copy(keepScreenOn = enabled) }
    }

    fun showAllHistory(enabled: Boolean) {
        mutable.update { it.copy(showAllHistory = enabled) }
    }

    private fun changeScore(side: ScoreSide, delta: Int) {
        val now = useCases.clock().epochMs
        session = useCases.addPoints(session, side, delta, now)
        persist()
    }

    private fun persist() {
        val clock = useCases.clock()
        val elapsed = session.elapsedMs(clock.monotonicMs)
        session = session.copy(
            accumulatedMs = elapsed,
            startedAtMs = if (session.running) clock.monotonicMs else null,
        )
        persistFields(clock)
        refresh(clock.monotonicMs)
    }

    private fun persistFields(clock: com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardClock) {
        val core = session.core
        saved["sbLeftName"] = core.leftName
        saved["sbRightName"] = core.rightName
        saved["sbLeftScore"] = core.leftScore
        saved["sbRightScore"] = core.rightScore
        saved["sbPeriod"] = core.period
        saved["sbStep"] = session.step
        saved["sbRunning"] = session.running
        saved["sbAccum"] = session.accumulatedMs
        saved["sbStarted"] = session.startedAtMs
        saved["sbJournal"] = ScoreboardPolicy.encodeJournal(session.journal)
        saved["sbSavedAt"] = clock.epochMs
    }

    private fun refresh(nowMonotonicMs: Long = useCases.clock().monotonicMs) {
        mutable.update {
            it.copy(
                session = session,
                displayMs = session.elapsedMs(nowMonotonicMs),
            )
        }
    }

    private fun runTicker() {
        ticker?.cancel()
        ticker = null
        if (!foreground || !session.running) return
        ticker = viewModelScope.launch {
            while (isActive && foreground && session.running) {
                refresh()
                delay(ScoreboardPolicy.TICK_MS)
            }
        }
    }

    private fun restoreSession(): ScoreboardSession {
        val clock = useCases.clock()
        val core = ScoreboardCore(
            leftName = ScoreboardPolicy.boundedName(saved.get<String>("sbLeftName").orEmpty()),
            rightName = ScoreboardPolicy.boundedName(saved.get<String>("sbRightName").orEmpty()),
            leftScore = (saved.get<Int>("sbLeftScore") ?: 0).coerceIn(0, ScoreboardPolicy.MAX_SCORE),
            rightScore = (saved.get<Int>("sbRightScore") ?: 0).coerceIn(0, ScoreboardPolicy.MAX_SCORE),
            period = (saved.get<Int>("sbPeriod") ?: 1).coerceIn(1, ScoreboardPolicy.MAX_PERIOD),
        )
        val running = saved.get<Boolean>("sbRunning") ?: false
        val accumulated = (saved.get<Long>("sbAccum") ?: 0L).coerceIn(0L, ScoreboardPolicy.MAX_TIMER_MS)
        val savedStarted = saved.get<Long>("sbStarted")
        val savedAt = saved.get<Long>("sbSavedAt")
            ?.takeIf { it in 0L..ScoreboardPolicy.MAX_EVENT_EPOCH_MS }
        val restoredElapsed = ScoreboardPolicy.restoreTimer(
            running = running,
            accumulatedMs = accumulated,
            startedAtMs = savedStarted,
            savedAtEpochMs = savedAt,
            nowMonotonicMs = clock.monotonicMs,
            nowEpochMs = clock.epochMs,
        )
        return ScoreboardSession(
            core = core,
            step = ScoreboardPolicy.normalizeStep(saved.get<Int>("sbStep") ?: 1),
            running = running,
            accumulatedMs = restoredElapsed,
            startedAtMs = if (running) clock.monotonicMs else null,
            journal = ScoreboardPolicy.decodeJournal(saved.get<ArrayList<String>>("sbJournal").orEmpty()),
        )
    }

    companion object {
        fun factory(useCases: ScoreboardUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                ScoreboardViewModel(useCases, extras.createSavedStateHandle()) as T
        }
    }
}
