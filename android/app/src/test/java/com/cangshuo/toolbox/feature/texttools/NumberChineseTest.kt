package com.cangshuo.toolbox.feature.texttools

import com.cangshuo.toolbox.feature.numberchinese.data.LocalNumberChineseRepository
import com.cangshuo.toolbox.feature.numberchinese.domain.ChineseNumberStyle
import com.cangshuo.toolbox.feature.numberchinese.domain.ConvertNumberUseCase
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseError
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseOutcome
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class NumberChineseTest {
    private val convert = ConvertNumberUseCase(LocalNumberChineseRepository())

    private fun lower(input: String, colloquial: Boolean = true) =
        (convert(input, ChineseNumberStyle.LOWER, NumberDirection.TO_CHINESE, colloquial) as NumberChineseOutcome.Success).text

    private fun upper(input: String) =
        (convert(input, ChineseNumberStyle.UPPER, NumberDirection.TO_CHINESE, false) as NumberChineseOutcome.Success).text

    private fun money(input: String) =
        (convert(input, ChineseNumberStyle.MONEY, NumberDirection.TO_CHINESE, false) as NumberChineseOutcome.Success).text

    private fun decode(input: String) =
        (convert(input, ChineseNumberStyle.LOWER, NumberDirection.TO_NUMBER, false) as NumberChineseOutcome.Success).text

    private fun failure(input: String, style: ChineseNumberStyle = ChineseNumberStyle.LOWER, direction: NumberDirection = NumberDirection.TO_CHINESE) =
        (convert(input, style, direction, false) as NumberChineseOutcome.Failure).error

    @Test
    fun lowerCaseBasics() {
        assertEquals("零", lower("0"))
        assertEquals("十", lower("10"))
        assertEquals("一十", lower("10", colloquial = false))
        assertEquals("十五", lower("15"))
        assertEquals("一百一十", lower("110"))
        assertEquals("一百零五", lower("105"))
        assertEquals("一千零一十五", lower("1015"))
        assertEquals("一万零一", lower("10001"))
        assertEquals("一亿", lower("100000000"))
        assertEquals("一亿零一万零一", lower("100010001"))
        assertEquals("一千二百三十四万五千六百七十八", lower("12345678"))
        assertEquals("负三点五", lower("-3.5"))
        assertEquals("零点五", lower("0.5"))
        assertEquals("一点五", lower("1.50"))
    }

    @Test
    fun upperCaseAndMoneyFormats() {
        assertEquals("壹仟贰佰叁拾肆", upper("1234"))
        assertEquals("人民币零元整", money("0"))
        assertEquals("人民币壹元整", money("1"))
        assertEquals("人民币壹元伍角", money("1.5"))
        assertEquals("人民币壹佰元零伍分", money("100.05"))
        assertEquals("人民币壹仟贰佰叁拾肆元伍角陆分", money("1234.56"))
        assertEquals("人民币伍角", money("0.5"))
        assertEquals("负人民币伍角", money("-0.5"))
        assertEquals(NumberChineseError.MONEY_DECIMALS, failure("1.234", ChineseNumberStyle.MONEY))
    }

    @Test
    fun decodeChineseNumeralsAndMoney() {
        assertEquals("15", decode("十五"))
        assertEquals("105", decode("一百零五"))
        assertEquals("10001", decode("一万零一"))
        assertEquals("100010001", decode("一亿零一万零一"))
        assertEquals("123", decode("壹佰贰拾叁"))
        assertEquals("230", decode("两百三十"))
        assertEquals("0.5", decode("零点五"))
        assertEquals("12.34", decode("十二点三四"))
        assertEquals("100.05", decode("人民币壹佰元零伍分"))
        assertEquals("1.5", decode("人民币壹元伍角"))
        assertEquals("1", decode("人民币壹元整"))
    }

    @Test
    fun invalidInputsAreReported() {
        assertEquals(NumberChineseError.INVALID_NUMBER, failure("12a"))
        assertEquals(NumberChineseError.INVALID_NUMBER, failure("1e5"))
        assertEquals(NumberChineseError.INVALID_CHINESE, failure("abc", direction = NumberDirection.TO_NUMBER))
        assertEquals(NumberChineseError.TOO_LONG, failure("1".repeat(61)))
    }

    @Test
    fun directionAutoDetection() {
        assertEquals(NumberDirection.TO_CHINESE, convert.detectDirection("123"))
        assertEquals(NumberDirection.TO_NUMBER, convert.detectDirection("十五"))
    }

    @Test
    fun largeIntegersRoundTripWithoutOverflowAndMalformedNumbersAreRejected() {
        for (number in listOf("100000000000000000000", "999999999999999999999999", "123456789012345678901234")) {
            assertEquals(number, decode(lower(number)))
        }
        for (input in listOf("-", "--1", ".", "负负1", "1.123456789")) {
            assertEquals(NumberChineseError.INVALID_NUMBER, failure(input))
        }
        for (input in listOf("一百百", "一百二百", "一万万")) {
            assertEquals(NumberChineseError.INVALID_CHINESE, failure(input, direction = NumberDirection.TO_NUMBER))
        }
        assertEquals("123", decode("一二三"))
    }
}
