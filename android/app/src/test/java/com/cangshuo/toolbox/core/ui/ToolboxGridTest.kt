package com.cangshuo.toolbox.core.ui

import org.junit.Assert.*
import org.junit.Test

class ToolboxGridTest {
    @Test fun narrowPanesReduceColumnsWithFontScale() {
        assertEquals(3,toolboxGridColumns(240f,1f,3))
        assertEquals(2,toolboxGridColumns(192f,1f,3))
        assertEquals(1,toolboxGridColumns(192f,2f,3))
        assertEquals(1,toolboxGridColumns(0f,1f,3))
    }
    @Test fun manualPreferenceIsBoundByAvailableSpace() {
        assertEquals(1,toolboxGridColumns(800f,1f,1))
        assertEquals(2,toolboxGridColumns(800f,1f,2))
        assertEquals(6,toolboxGridColumns(800f,1f))
        assertEquals(3,toolboxGridColumns(288f,1.3f,4))
    }
}
