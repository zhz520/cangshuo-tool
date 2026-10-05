package com.cangshuo.toolbox.feature.level

import com.cangshuo.toolbox.feature.level.domain.LevelMath
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class LevelMathTest {
    @Test fun flatAndKnownTiltAndDisplayRotations() {
        val flat = LevelMath.fromGravity(0.0,0.0,9.81,0)!!
        assertTrue(LevelMath.nearZero(flat));assertEquals(0.0,flat.tilt,0.0)
        val x = 9.81*sin(Math.PI/6);val z = 9.81*cos(Math.PI/6)
        val a = LevelMath.fromGravity(x,0.0,z,0)!!
        assertEquals(30.0,a.x,1e-10);assertEquals(30.0,a.tilt,1e-10)
        assertEquals(-30.0,LevelMath.fromGravity(x,0.0,z,1)!!.y,1e-10)
        assertEquals(-30.0,LevelMath.fromGravity(x,0.0,z,2)!!.x,1e-10)
        assertEquals(30.0,LevelMath.fromGravity(x,0.0,z,3)!!.y,1e-10)
        assertTrue(LevelMath.nearZero(LevelMath.relative(a,a)))
    }
    @Test fun faceDownFreeFallAndNonFiniteAreNotLevel() {
        assertFalse(LevelMath.nearZero(LevelMath.fromGravity(0.0,0.0,-9.81,0)!!))
        assertNull(LevelMath.fromGravity(0.0,0.0,0.0,0));assertNull(LevelMath.fromGravity(0.0,0.0,30.0,0))
        assertNull(LevelMath.fromGravity(Double.NaN,0.0,9.81,0))
        assertNull(LevelMath.fromGravity(Double.MAX_VALUE,Double.MAX_VALUE,9.81,0))
    }
}
