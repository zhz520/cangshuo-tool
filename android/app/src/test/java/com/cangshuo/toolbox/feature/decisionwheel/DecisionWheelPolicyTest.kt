package com.cangshuo.toolbox.feature.decisionwheel

import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelError
import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelPolicy
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelOption
import com.cangshuo.toolbox.feature.decisionwheel.ui.wheelSegmentLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionWheelPolicyTest {
    private val weighted = listOf(
        WheelOption("A", 1),
        WheelOption("B", 3),
        WheelOption("C", 2),
    )

    @Test fun weightedTicketsRespectEveryBoundary() {
        assertEquals(0, DecisionWheelPolicy.chooseWinner(weighted, null) { 0 })
        assertEquals(1, DecisionWheelPolicy.chooseWinner(weighted, null) { 1 })
        assertEquals(1, DecisionWheelPolicy.chooseWinner(weighted, null) { 3 })
        assertEquals(2, DecisionWheelPolicy.chooseWinner(weighted, null) { 4 })
        assertEquals(2, DecisionWheelPolicy.chooseWinner(weighted, null) { 5 })
    }

    @Test fun avoidRepeatRemovesPreviousBeforeWeightedDraw() {
        val winner = DecisionWheelPolicy.chooseWinner(weighted, "B") { 2 }
        assertEquals(2, winner)
        assertEquals(1, DecisionWheelPolicy.chooseWinner(listOf(WheelOption("A"), WheelOption("B")), "A") { 0 })
        assertEquals(1, DecisionWheelPolicy.chooseWinner(listOf(WheelOption("A"), WheelOption("B")), "A") { 1 })
    }

    @Test fun targetRotationPutsPointerInsideWinnerSegment() {
        val target = DecisionWheelPolicy.targetRotation(
            currentRotation = 20f,
            options = weighted,
            winnerIndex = 1,
            fractionInsideSegment = 0.5f,
            turns = 5,
        )
        assertEquals(2_010f, target, 0.01f)
        val pointerPositionFromTop = 60f + 180f * 0.5f
        assertEquals(0f, DecisionWheelPolicy.floorMod(pointerPositionFromTop + target, 360f), 0.01f)
    }

    @Test fun weightsPercentagesAndTurnsAreBounded() {
        assertEquals(50, DecisionWheelPolicy.percent(3, weighted))
        val first = DecisionWheelPolicy.targetRotation(0f, weighted, 0, 0.5f, 0)
        val bounded = DecisionWheelPolicy.targetRotation(0f, weighted, 0, 0.5f, 99)
        assertEquals(720f, bounded - first, 0.01f)
        assertEquals(10f, DecisionWheelPolicy.displayWeight(99), 0f)
    }

    @Test fun labelsAreCleanedAndValidatedAsAUnit() {
        assertEquals("red apple", DecisionWheelPolicy.cleanLabel("  red\n\t apple  "))
        assertEquals("red apple", DecisionWheelPolicy.cleanLabel("red\napple"))
        assertEquals(24, DecisionWheelPolicy.cleanLabel("x".repeat(40)).length)
        val options = listOf(WheelOption("Apple"), WheelOption("Pear"))
        assertEquals(DecisionWheelError.EMPTY, DecisionWheelPolicy.validate(options, "  ", 1, null))
        assertEquals(DecisionWheelError.LENGTH, DecisionWheelPolicy.validate(options, "x".repeat(25), 1, null))
        assertEquals(DecisionWheelError.DUPLICATE, DecisionWheelPolicy.validate(options, "ＡＰＰＬＥ", 1, null))
        assertEquals(DecisionWheelError.WEIGHT, DecisionWheelPolicy.validate(options, "Orange", 0, null))
        assertNull(DecisionWheelPolicy.validate(options, "Apple", 2, 0))
    }

    @Test fun optionLimitsAndAvailabilityAreEnforced() {
        val full = (1..DecisionWheelPolicy.MAX_OPTIONS).map { WheelOption("Item $it") }
        assertEquals(DecisionWheelError.LIMIT, DecisionWheelPolicy.validate(full, "Extra", 1, null))
        assertNull(DecisionWheelPolicy.chooseWinner(full.take(1), null) { 0 })
        assertTrue(full.size >= DecisionWheelPolicy.MIN_OPTIONS)
        assertFalse(full.isEmpty())
    }

    @Test fun narrowSegmentsUseTheNumberedLegend() {
        assertEquals("1", wheelSegmentLabel(0, WheelOption("A long label"), 10f))
        assertEquals("A long label", wheelSegmentLabel(0, WheelOption("A long label"), 18f))
    }
}
