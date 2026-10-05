package com.cangshuo.toolbox.feature.converter.ui

import com.cangshuo.toolbox.feature.converter.domain.ConversionType
import com.cangshuo.toolbox.feature.converter.domain.UnitDef

/** Immutable snapshot presented by [ConverterViewModel]. */
data class ConverterUiState(
    val selectedType: ConversionType = ConversionType.LENGTH,
    val units: List<UnitDef> = emptyList(),
    val fromUnit: UnitDef? = null,
    val toUnit: UnitDef? = null,
    val inputValue: String = "",
    val result: String = "",
    val isInputError: Boolean = false,
    val isConversionError: Boolean = false,
)
