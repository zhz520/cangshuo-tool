package com.cangshuo.toolbox.feature.calculator.data

import com.cangshuo.toolbox.feature.calculator.domain.CalculationError
import com.cangshuo.toolbox.feature.calculator.domain.CalculationResult
import com.cangshuo.toolbox.feature.calculator.domain.CalculatorRepository
import com.cangshuo.toolbox.feature.calculator.domain.MAX_EXPRESSION_LENGTH
import java.math.BigDecimal
import java.math.MathContext

private const val MAX_NUMBER_LENGTH = 128
private const val MAX_RESULT_LENGTH = 128
private const val MAX_PARENTHESES_DEPTH = 32
private val arithmeticContext = MathContext.DECIMAL128

class LocalCalculatorRepository : CalculatorRepository {
    override fun calculate(expression: String): CalculationResult {
        if (expression.length > MAX_EXPRESSION_LENGTH) {
            return CalculationResult.Failure(CalculationError.INPUT_TOO_LONG)
        }
        if (expression.isBlank()) return CalculationResult.Failure(CalculationError.EMPTY_EXPRESSION)
        return try {
            val value = ExpressionParser(expression.replace('×', '*').replace('÷', '/').replace('−', '-')).parse()
            val formatted = if (value.signum() == 0) "0" else value.stripTrailingZeros().toPlainString()
            if (formatted.length > MAX_RESULT_LENGTH) {
                CalculationResult.Failure(CalculationError.OUT_OF_RANGE)
            } else {
                CalculationResult.Success(formatted)
            }
        } catch (failure: ParseFailure) {
            CalculationResult.Failure(failure.error)
        } catch (_: NumberFormatException) {
            CalculationResult.Failure(CalculationError.INVALID_EXPRESSION)
        } catch (_: ArithmeticException) {
            CalculationResult.Failure(CalculationError.CALCULATION_FAILED)
        }
    }
}

private class ParseFailure(val error: CalculationError) : RuntimeException()

/** Recursive descent with arithmetic precedence; postfix percent divides a value by 100. */
private class ExpressionParser(private val source: String) {
    private var position = 0
    private var depth = 0

    fun parse(): BigDecimal {
        val value = expression()
        skipWhitespace()
        if (position != source.length) fail(CalculationError.INVALID_EXPRESSION)
        return value
    }

    private fun expression(): BigDecimal {
        var value = term()
        while (true) {
            value = when {
                consume('+') -> value.add(term(), arithmeticContext)
                consume('-') -> value.subtract(term(), arithmeticContext)
                else -> return value
            }
        }
    }

    private fun term(): BigDecimal {
        var value = unary()
        while (true) {
            value = when {
                consume('*') -> value.multiply(unary(), arithmeticContext)
                consume('/') -> {
                    val divisor = unary()
                    if (divisor.signum() == 0) fail(CalculationError.DIVISION_BY_ZERO)
                    value.divide(divisor, arithmeticContext)
                }
                else -> return value
            }
        }
    }

    private fun unary(): BigDecimal {
        var negative = false
        while (true) {
            when {
                consume('+') -> Unit
                consume('-') -> negative = !negative
                else -> break
            }
        }
        var value = primary()
        while (consume('%')) value = value.divide(BigDecimal("100"), arithmeticContext)
        return if (negative) value.negate(arithmeticContext) else value
    }

    private fun primary(): BigDecimal {
        if (consume('(')) {
            if (depth >= MAX_PARENTHESES_DEPTH) fail(CalculationError.TOO_COMPLEX)
            depth++
            val value = expression()
            if (!consume(')')) fail(CalculationError.INVALID_EXPRESSION)
            depth--
            return value
        }
        skipWhitespace()
        val start = position
        var digitCount = 0
        while (position < source.length && source[position] in '0'..'9') {
            position++
            digitCount++
        }
        if (position < source.length && source[position] == '.') {
            position++
            while (position < source.length && source[position] in '0'..'9') {
                position++
                digitCount++
            }
        }
        if (digitCount == 0) fail(CalculationError.INVALID_EXPRESSION)
        if (position - start > MAX_NUMBER_LENGTH) fail(CalculationError.OUT_OF_RANGE)
        return BigDecimal(source.substring(start, position), arithmeticContext)
    }

    private fun consume(token: Char): Boolean {
        skipWhitespace()
        if (position < source.length && source[position] == token) {
            position++
            return true
        }
        return false
    }

    private fun skipWhitespace() {
        while (position < source.length && source[position].isWhitespace()) position++
    }

    private fun fail(error: CalculationError): Nothing = throw ParseFailure(error)
}
