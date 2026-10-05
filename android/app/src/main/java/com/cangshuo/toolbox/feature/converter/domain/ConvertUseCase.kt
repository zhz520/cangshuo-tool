package com.cangshuo.toolbox.feature.converter.domain

import java.math.BigDecimal

/** Converts a numeric value between units, wrapping errors into a sealed result. */
class ConvertUseCase(private val repository: ConverterRepository) {

    fun getUnits(type: ConversionType): List<UnitDef> = repository.getUnits(type)

    fun convert(
        input: String,
        from: UnitDef,
        to: UnitDef,
        type: ConversionType,
    ): ConversionResult {
        if (input.length > ConversionNumberPolicy.MAX_INPUT_LENGTH) {
            return ConversionResult.InvalidInput
        }
        val value = try {
            BigDecimal(input.trim())
        } catch (_: NumberFormatException) {
            return ConversionResult.InvalidInput
        }
        return convert(value, from, to, type)
    }

    fun convert(
        value: BigDecimal,
        from: UnitDef,
        to: UnitDef,
        type: ConversionType,
    ): ConversionResult {
        if (!ConversionNumberPolicy.isSupported(value)) return ConversionResult.Error
        return try {
            val result = repository.convert(value, from, to, type)
            if (ConversionNumberPolicy.isSupported(result)) {
                ConversionResult.Success(result, ConversionNumberPolicy.format(result))
            } else {
                ConversionResult.Error
            }
        } catch (_: ArithmeticException) {
            ConversionResult.Error
        }
    }
}

/** Result of a unit conversion attempt. */
sealed interface ConversionResult {
    data class Success(val value: BigDecimal, val formattedValue: String) : ConversionResult
    data object InvalidInput : ConversionResult
    data object Error : ConversionResult
}
