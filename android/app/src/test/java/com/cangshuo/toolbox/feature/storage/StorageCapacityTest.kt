package com.cangshuo.toolbox.feature.storage

import com.cangshuo.toolbox.feature.storage.domain.StorageCapacity
import org.junit.Assert.*
import org.junit.Test

class StorageCapacityTest {
    @Test fun rejectsInconsistentOrUnavailableCounters() {
        listOf(Triple(0L,0L,0L), Triple(-1L,0L,0L), Triple(100L,101L,5L),
            Triple(100L,20L,21L), Triple(100L,-1L,0L), Triple(100L,20L,-1L)).forEach {
            assertNull(StorageCapacity.create(it.first,it.second,it.third))
        }
    }
    @Test fun largeCountersDoNotOverflowAndReservedIsSeparateFromUsed() {
        val capacity = StorageCapacity.create(Long.MAX_VALUE,Long.MAX_VALUE/2,Long.MAX_VALUE/4)!!
        assertEquals(Long.MAX_VALUE-Long.MAX_VALUE/2,capacity.used)
        assertEquals(Long.MAX_VALUE/2-Long.MAX_VALUE/4,capacity.reserved)
        assertEquals(.5f,capacity.usedFraction,.0001f)
        assertEquals(1f,StorageCapacity.create(100,0,0)!!.usedFraction,0f)
        assertEquals(0f,StorageCapacity.create(100,100,100)!!.usedFraction,0f)
    }
}
