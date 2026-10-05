package com.cangshuo.toolbox.feature.battery

import com.cangshuo.toolbox.feature.battery.domain.BatteryPolicy
import org.junit.Assert.*
import org.junit.Test

class BatteryPolicyTest {
    @Test fun invalidAndMissingScaleNeverProducesFalseZeroOrInfinity() {
        assertNull(BatteryPolicy.percent(1,0)); assertNull(BatteryPolicy.percent(-1,100))
        assertNull(BatteryPolicy.percent(101,100)); assertNull(BatteryPolicy.percent(null,100))
        assertEquals(50.0,BatteryPolicy.percent(100,200)!!,0.0)
        assertEquals(100.0,BatteryPolicy.percent(Int.MAX_VALUE,Int.MAX_VALUE)!!,0.0)
    }
    @Test fun negativeTemperatureIsValidButMissingAndSentinelAreUnknown() {
        assertEquals(-.1,BatteryPolicy.temperature(-1)!!,0.0)
        assertEquals(32.5,BatteryPolicy.temperature(325)!!,0.0)
        assertNull(BatteryPolicy.temperature(null)); assertNull(BatteryPolicy.temperature(Int.MIN_VALUE))
        assertNull(BatteryPolicy.property(Int.MIN_VALUE)); assertEquals(-250000,BatteryPolicy.property(-250000))
        assertEquals(0,BatteryPolicy.property(0))
    }
}
