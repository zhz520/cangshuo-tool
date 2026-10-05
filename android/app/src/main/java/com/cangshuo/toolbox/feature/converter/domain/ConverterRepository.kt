package com.cangshuo.toolbox.feature.converter.domain

import java.math.BigDecimal

/** Provides units and conversion logic for the unit converter tool. */
interface ConverterRepository {
    /** Returns the ordered list of units for the given conversion type. */
    fun getUnits(type: ConversionType): List<UnitDef>

    /**
     * Converts [value] from [from] unit to [to] unit within the given [type].
     * @throws ArithmeticException if the conversion produces a value that cannot be represented.
     */
    fun convert(value: BigDecimal, from: UnitDef, to: UnitDef, type: ConversionType): BigDecimal
}
