package com.cangshuo.toolbox.feature.scoreboard.data

import android.os.SystemClock
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardClock
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardRepository

class LocalScoreboardRepository : ScoreboardRepository {
    override fun clock(): ScoreboardClock = ScoreboardClock(
        monotonicMs = SystemClock.elapsedRealtime(),
        epochMs = System.currentTimeMillis(),
    )
}
