package com.cangshuo.toolbox.feature.timer.data

import android.content.Context
import com.cangshuo.toolbox.feature.timer.domain.CountdownPolicy
import com.cangshuo.toolbox.feature.timer.domain.CountdownRepository
import com.cangshuo.toolbox.feature.timer.domain.CountdownStatus
import com.cangshuo.toolbox.feature.timer.service.CountdownTimerService
import kotlinx.coroutines.flow.StateFlow

class LocalCountdownRepository(context: Context) : CountdownRepository {
    private val appContext = context.applicationContext
    private val store = CountdownTimerStore(appContext)
    override val state: StateFlow<com.cangshuo.toolbox.feature.timer.domain.CountdownTimerState> = store.state

    override fun prepare(durationMillis: Long) {
        val current = store.read()
        if (current.status == CountdownStatus.RUNNING || current.status == CountdownStatus.PAUSED) return
        store.save(CountdownPolicy.fresh(durationMillis, current))
    }

    override fun start(durationMillis: Long, label: String) {
        store.save(
            CountdownPolicy.start(
                current = store.read(),
                durationMillis = durationMillis,
                label = label,
                nowElapsedMillis = store.nowElapsedMillis(),
                nowWallMillis = store.nowWallMillis(),
            ),
        )
        CountdownTimerService.start(appContext)
    }

    override fun pause() {
        store.save(CountdownPolicy.pause(store.read(), store.nowElapsedMillis()))
    }

    override fun resume() {
        store.save(
            CountdownPolicy.resume(store.read(), store.nowElapsedMillis(), store.nowWallMillis()),
        )
    }

    override fun reset() {
        val current = store.read()
        store.save(CountdownPolicy.fresh(current.totalMillis, current))
        CountdownTimerService.stop(appContext)
    }

    override fun extend(additionalMillis: Long) {
        store.save(
            CountdownPolicy.extend(store.read(), additionalMillis, store.nowElapsedMillis(), store.nowWallMillis()),
        )
    }

    override fun finish(): Boolean {
        val current = store.read()
        if (current.status != CountdownStatus.RUNNING || current.remainingAt(store.nowElapsedMillis()) > 0L) {
            return false
        }
        store.save(CountdownPolicy.finish(current))
        return true
    }

    override fun markCompletionNotified() {
        store.save(store.read().copy(completionNotified = true))
    }

    override fun setKeepScreenOn(enabled: Boolean) {
        store.save(store.read().copy(keepScreenOn = enabled))
    }

    override fun ensureService() {
        if (CountdownPolicy.requiresForegroundService(store.read())) {
            CountdownTimerService.start(appContext)
        }
    }

    override fun nowElapsedMillis(): Long = store.nowElapsedMillis()
    override fun nowWallMillis(): Long = store.nowWallMillis()
    fun bootCount(): Int = store.bootCount()
}
