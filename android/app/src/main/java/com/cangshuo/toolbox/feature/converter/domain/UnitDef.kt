package com.cangshuo.toolbox.feature.converter.domain

import androidx.annotation.StringRes
import java.math.BigDecimal

/**
 * A single unit within a conversion category.
 *
 * @param id Unique identifier (e.g. "meter", "kilogram").
 * @param symbol Display symbol (e.g. "m", "kg"). Universal, not translated.
 * @param nameResId String resource for the localized full unit name.
 * @param factor Conversion factor: 1 of this unit equals [factor] base units.
 *               For temperature units this value is ignored; temperature
 *               conversion uses dedicated formulas in the repository.
 */
data class UnitDef(
    val id: String,
    val symbol: String,
    @param:StringRes val nameResId: Int,
    val factor: BigDecimal,
)
