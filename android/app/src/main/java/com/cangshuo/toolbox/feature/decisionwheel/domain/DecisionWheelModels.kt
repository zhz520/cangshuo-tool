package com.cangshuo.toolbox.feature.decisionwheel.domain

import com.cangshuo.toolbox.core.model.UnicodeText
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

data class WheelOption(val label: String, val weight: Int = 1)

data class WheelSettings(
    val options: List<WheelOption>,
    val avoidRepeat: Boolean = true,
    val haptics: Boolean = true,
)

data class WheelResult(val label: String, val timestampMillis: Long)

data class WheelSpin(
    val id: Long,
    val winnerIndex: Int,
    val targetRotation: Float,
)

enum class WheelPreset { MEALS, YES_NO, ACTIVITIES }

enum class DecisionWheelError { EMPTY, LENGTH, DUPLICATE, WEIGHT, LIMIT, NOT_ENOUGH }

interface DecisionWheelRepository {
    fun defaults(): List<WheelOption>

    fun preset(kind: WheelPreset): List<WheelOption>

    fun nextInt(bound: Int): Int

    fun nowMillis(): Long
}

class DecisionWheelUseCases(private val repository: DecisionWheelRepository) {
    fun defaults(): List<WheelOption> = repository.defaults().take(DecisionWheelPolicy.MAX_OPTIONS)

    fun preset(kind: WheelPreset): List<WheelOption> =
        repository.preset(kind).take(DecisionWheelPolicy.MAX_OPTIONS)

    fun validate(
        options: List<WheelOption>,
        label: String,
        weight: Int,
        editingIndex: Int?,
    ): DecisionWheelError? = DecisionWheelPolicy.validate(options, label, weight, editingIndex)

    fun option(label: String, weight: Int): WheelOption = WheelOption(
        label = DecisionWheelPolicy.cleanLabel(label),
        weight = weight.coerceIn(DecisionWheelPolicy.MIN_WEIGHT, DecisionWheelPolicy.MAX_WEIGHT),
    )

    fun plan(
        options: List<WheelOption>,
        currentRotation: Float,
        avoidRepeat: Boolean,
        lastLabel: String?,
        spinId: Long,
    ): WheelSpin? {
        val winner = DecisionWheelPolicy.chooseWinner(options, lastLabel.takeIf { avoidRepeat }) {
            repository.nextInt(it)
        } ?: return null
        val extraTurns = DecisionWheelPolicy.MIN_TURNS + repository.nextInt(DecisionWheelPolicy.EXTRA_TURNS + 1)
        val fraction = 0.22f + repository.nextInt(57) / 100f
        return WheelSpin(
            id = spinId,
            winnerIndex = winner,
            targetRotation = DecisionWheelPolicy.targetRotation(
                currentRotation = currentRotation,
                options = options,
                winnerIndex = winner,
                fractionInsideSegment = fraction,
                turns = extraTurns,
            ),
        )
    }

    fun result(spin: WheelSpin, options: List<WheelOption>): WheelResult? {
        val option = options.getOrNull(spin.winnerIndex) ?: return null
        return WheelResult(option.label, repository.nowMillis())
    }
}

object DecisionWheelPolicy {
    const val MIN_OPTIONS = 2
    const val MAX_OPTIONS = 12
    const val MIN_WEIGHT = 1
    const val MAX_WEIGHT = 10
    const val MAX_LABEL_CODE_POINTS = 24
    const val MAX_HISTORY = 30
    const val MIN_TURNS = 4
    const val EXTRA_TURNS = 2

    fun cleanLabel(value: String): String = value
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .filterNot { it.isISOControl() }
        .trim()
        .replace(Regex("\\s+"), " ")
        .let { UnicodeText.takeCodePointsWithoutSplittingCluster(it, MAX_LABEL_CODE_POINTS) }

    fun limitLabel(value: String): String =
        UnicodeText.takeCodePointsWithoutSplittingCluster(value, MAX_LABEL_CODE_POINTS)

    fun validate(
        options: List<WheelOption>,
        label: String,
        weight: Int,
        editingIndex: Int?,
    ): DecisionWheelError? {
        val clean = cleanLabel(label)
        if (clean.isEmpty()) return DecisionWheelError.EMPTY
        if (label.codePointCount(0, label.length) > MAX_LABEL_CODE_POINTS) {
            return DecisionWheelError.LENGTH
        }
        if (weight !in MIN_WEIGHT..MAX_WEIGHT) return DecisionWheelError.WEIGHT
        if (editingIndex == null && options.size >= MAX_OPTIONS) return DecisionWheelError.LIMIT
        val normalized = normalize(clean)
        if (options.withIndex().any { (index, option) ->
                index != editingIndex && normalize(option.label) == normalized
            }
        ) return DecisionWheelError.DUPLICATE
        return null
    }

    fun chooseWinner(
        options: List<WheelOption>,
        avoidLabel: String?,
        nextInt: (Int) -> Int,
    ): Int? {
        if (options.size < MIN_OPTIONS) return null
        val eligible = options.indices.filter { index ->
            avoidLabel == null || !options[index].label.equals(avoidLabel, ignoreCase = true)
        }.ifEmpty { options.indices.toList() }
        val total = eligible.sumOf { options[it].weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT) }
        if (total <= 0) return null
        var ticket = nextInt(total).coerceIn(0, total - 1)
        for (index in eligible) {
            val weight = options[index].weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT)
            if (ticket < weight) return index
            ticket -= weight
        }
        return eligible.lastOrNull()
    }

    /** Rotation is clockwise from the fixed top pointer. */
    fun targetRotation(
        currentRotation: Float,
        options: List<WheelOption>,
        winnerIndex: Int,
        fractionInsideSegment: Float,
        turns: Int,
    ): Float {
        require(options.isNotEmpty())
        require(winnerIndex in options.indices)
        require(turns >= 0)
        val total = options.sumOf { it.weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT) }.coerceAtLeast(1)
        val before = options.take(winnerIndex).sumOf { it.weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT) }
        val sweep = 360f * options[winnerIndex].weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT) / total
        val pointerPosition = (before * 360f / total) + sweep * fractionInsideSegment.coerceIn(0.05f, 0.95f)
        val desiredModulo = floorMod(-pointerPosition, 360f)
        val currentModulo = floorMod(currentRotation, 360f)
        val alignment = floorMod(desiredModulo - currentModulo, 360f)
        return currentRotation + turns.coerceIn(MIN_TURNS, MIN_TURNS + EXTRA_TURNS) * 360f + alignment
    }

    fun floorMod(value: Float, modulus: Float): Float = ((value % modulus) + modulus) % modulus

    fun displayWeight(weight: Int): Float = weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT).toFloat()

    fun percent(weight: Int, options: List<WheelOption>): Int {
        val total = options.sumOf { it.weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT) }.coerceAtLeast(1)
        return (weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT) * 100f / total).roundToInt()
    }

    private fun normalize(label: String): String =
        Normalizer.normalize(label, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}
