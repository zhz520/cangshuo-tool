package com.cangshuo.toolbox.feature.compass

import com.cangshuo.toolbox.feature.compass.domain.CompassAngles
import org.junit.Assert.*
import org.junit.Test

class CompassAnglesTest {
    @Test fun wrapsNorthAndRejectsInvalidNumbers() {
        assertEquals(359.0,CompassAngles.normalize(-1.0)!!,0.0)
        assertEquals(0.0,CompassAngles.normalize(720.0)!!,0.0)
        assertNull(CompassAngles.normalize(Double.NaN)); assertNull(CompassAngles.normalize(Double.POSITIVE_INFINITY))
    }
    @Test fun sectorsAgreeAtWrapAndHalfSectorBoundaries() {
        assertEquals(0,CompassAngles.sector(359.0)); assertEquals(0,CompassAngles.sector(22.49))
        assertEquals(1,CompassAngles.sector(22.5)); assertEquals(4,CompassAngles.sector(180.0))
        assertEquals(0,CompassAngles.sector(337.5)); assertEquals(7,CompassAngles.sector(337.49))
    }
}
