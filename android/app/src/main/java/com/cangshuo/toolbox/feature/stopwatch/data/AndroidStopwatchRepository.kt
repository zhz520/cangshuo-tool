package com.cangshuo.toolbox.feature.stopwatch.data

import android.os.SystemClock
import com.cangshuo.toolbox.feature.stopwatch.domain.StopwatchRepository

/** Monotonic clock: unaffected by wall-clock or timezone changes. */
class AndroidStopwatchRepository : StopwatchRepository {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
}
