package com.cangshuo.toolbox.feature.decisionwheel.data

import android.content.res.Resources
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelRepository
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelOption
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelPreset
import java.security.SecureRandom

class LocalDecisionWheelRepository(resources: Resources) : DecisionWheelRepository {
    private var resources = resources
    private val random = SecureRandom()

    fun updateResources(resources: Resources) {
        this.resources = resources
    }

    override fun defaults(): List<WheelOption> = options(R.array.decision_wheel_default_options)

    override fun preset(kind: WheelPreset): List<WheelOption> = when (kind) {
        WheelPreset.MEALS -> options(R.array.decision_wheel_preset_meals)
        WheelPreset.YES_NO -> options(R.array.decision_wheel_preset_yes_no)
        WheelPreset.ACTIVITIES -> options(R.array.decision_wheel_preset_activities)
    }

    override fun nextInt(bound: Int): Int {
        require(bound > 0)
        return random.nextInt(bound)
    }

    override fun nowMillis(): Long = System.currentTimeMillis()

    private fun options(arrayId: Int): List<WheelOption> =
        resources.getStringArray(arrayId).map { WheelOption(it.trim(), 1) }
}
