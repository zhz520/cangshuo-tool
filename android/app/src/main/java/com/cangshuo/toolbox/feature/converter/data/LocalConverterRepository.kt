package com.cangshuo.toolbox.feature.converter.data

import com.cangshuo.toolbox.feature.converter.domain.ConverterRepository
import com.cangshuo.toolbox.feature.converter.domain.ConversionType
import com.cangshuo.toolbox.feature.converter.domain.UnitDef
import java.math.BigDecimal
import java.math.MathContext

/** Pure-local unit converter. No API, database or file access. */
class LocalConverterRepository : ConverterRepository {
    private val mc = MathContext.DECIMAL128

    override fun getUnits(type: ConversionType): List<UnitDef> = UnitData.unitsFor(type)

    override fun convert(
        value: BigDecimal,
        from: UnitDef,
        to: UnitDef,
        type: ConversionType,
    ): BigDecimal {
        if (from.id == to.id) return value
        return if (type == ConversionType.TEMPERATURE) {
            convertTemperature(value, from.id, to.id)
        } else {
            // value_in_base = value × from.factor ; result = value_in_base / to.factor
            value.multiply(from.factor, mc).divide(to.factor, mc)
        }
    }

    // ── Temperature: dedicated formulas via Celsius intermediary ─────────

    private fun convertTemperature(value: BigDecimal, fromId: String, toId: String): BigDecimal {
        val celsius = when (fromId) {
            "celsius" -> value
            "fahrenheit" -> value.subtract(BD_32, mc).multiply(BD_5, mc).divide(BD_9, mc)
            "kelvin" -> value.subtract(BD_273_15, mc)
            else -> throw IllegalArgumentException("Unknown temperature unit: $fromId")
        }
        return when (toId) {
            "celsius" -> celsius
            "fahrenheit" -> celsius.multiply(BD_9, mc).divide(BD_5, mc).add(BD_32, mc)
            "kelvin" -> celsius.add(BD_273_15, mc)
            else -> throw IllegalArgumentException("Unknown temperature unit: $toId")
        }
    }

    private companion object {
        val BD_5: BigDecimal = BigDecimal("5")
        val BD_9: BigDecimal = BigDecimal("9")
        val BD_32: BigDecimal = BigDecimal("32")
        val BD_273_15: BigDecimal = BigDecimal("273.15")
    }
}
