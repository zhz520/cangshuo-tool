package com.cangshuo.toolbox.feature.imagecompress

import com.cangshuo.toolbox.feature.imagecompress.domain.FlipMode
import com.cangshuo.toolbox.feature.imagecompress.domain.scaleFactors
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCompressFlipTest {
    @Test fun flipModesMapToExpectedAxisScales() {
        assertEquals(1f to 1f, FlipMode.NONE.scaleFactors())
        assertEquals(-1f to 1f, FlipMode.HORIZONTAL.scaleFactors())
        assertEquals(1f to -1f, FlipMode.VERTICAL.scaleFactors())
        assertEquals(-1f to -1f, FlipMode.BOTH.scaleFactors())
    }
}
