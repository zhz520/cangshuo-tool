package com.cangshuo.toolbox.feature.numberchinese.data

import com.cangshuo.toolbox.feature.numberchinese.domain.ChineseNumberStyle
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseError
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseLimits
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseOutcome
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberChineseRepository
import com.cangshuo.toolbox.feature.numberchinese.domain.NumberDirection
import java.math.BigInteger

/**
 * Local conversion between Arabic numbers and Chinese numerals. The encoding rules follow the
 * widely used conventions implemented by cnwhy/nzh (BSD-2-Clause): four-digit grouping with
 * 万/亿/万亿/亿亿 units, 零 collapsing, the colloquial 十 for two-digit teens and the
 * 人民币…元/角/分…整 money format. The decoder is an independent section-based parser.
 */
class LocalNumberChineseRepository : NumberChineseRepository {

    private val lowerDigits = "零一二三四五六七八九"
    private val upperDigits = "零壹贰叁肆伍陆柒捌玖"
    private val groupUnits = listOf("", "万", "亿", "万亿", "亿亿", "万亿亿")

    override fun detectDirection(input: String): NumberDirection =
        if (input.any { it in '0'..'9' }) NumberDirection.TO_CHINESE else NumberDirection.TO_NUMBER

    override fun convert(
        input: String,
        style: ChineseNumberStyle,
        direction: NumberDirection,
        colloquialTen: Boolean,
    ): NumberChineseOutcome {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return NumberChineseOutcome.Failure(NumberChineseError.EMPTY)
        if (trimmed.length > NumberChineseLimits.MAX_INPUT_LENGTH) {
            return NumberChineseOutcome.Failure(NumberChineseError.TOO_LONG)
        }
        val resolved = if (direction == NumberDirection.AUTO) detectDirection(trimmed) else direction
        return when (resolved) {
            NumberDirection.TO_CHINESE -> encode(trimmed, style, colloquialTen)
            NumberDirection.TO_NUMBER -> decode(trimmed, style)
            NumberDirection.AUTO -> NumberChineseOutcome.Failure(NumberChineseError.INVALID_NUMBER)
        }
    }

    // ------------------------------------------------------------------ encode

    private fun encode(input: String, style: ChineseNumberStyle, colloquialTen: Boolean): NumberChineseOutcome {
        if (!Regex("[-负]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)").matches(input)) {
            return NumberChineseOutcome.Failure(NumberChineseError.INVALID_NUMBER)
        }
        val negative = input.startsWith("-") || input.startsWith("负")
        val body = input.trimStart('-', '负')
        val parts = body.split('.')
        if (parts.size > 2) return NumberChineseOutcome.Failure(NumberChineseError.INVALID_NUMBER)
        val integerText = parts[0].ifEmpty { "0" }
        val decimals = parts.getOrElse(1) { "" }
        if (!integerText.all { it in '0'..'9' } || !decimals.all { it in '0'..'9' }) {
            return NumberChineseOutcome.Failure(NumberChineseError.INVALID_NUMBER)
        }
        if (integerText.trimStart('0').length > NumberChineseLimits.MAX_INTEGER_DIGITS) {
            return NumberChineseOutcome.Failure(NumberChineseError.INVALID_NUMBER)
        }
        val digits = if (style == ChineseNumberStyle.LOWER) lowerDigits else upperDigits
        val trimmedDecimals = decimals.trimEnd('0')
        if (decimals.length > NumberChineseLimits.MAX_DECIMALS) return NumberChineseOutcome.Failure(NumberChineseError.INVALID_NUMBER)
        if (style == ChineseNumberStyle.MONEY) {
            if (decimals.length > 2) return NumberChineseOutcome.Failure(NumberChineseError.MONEY_DECIMALS)
            return NumberChineseOutcome.Success(money(integerText, decimals, negative))
        }
        val integer = encodeInteger(integerText, style, colloquialTen)
        val decimalText = if (trimmedDecimals.isEmpty()) "" else "点" + trimmedDecimals.map { digits[it - '0'] }.joinToString("")
        return NumberChineseOutcome.Success((if (negative) "负" else "") + integer + decimalText)
    }

    private fun encodeInteger(digitsText: String, style: ChineseNumberStyle, colloquialTen: Boolean): String {
        val trimmed = digitsText.trimStart('0')
        if (trimmed.isEmpty()) return "零"
        val groups = ArrayList<String>()
        var end = trimmed.length
        while (end > 0) {
            val start = maxOf(0, end - 4)
            groups.add(trimmed.substring(start, end))
            end = start
        }
        val builder = StringBuilder()
        var pendingZero = false
        for (index in groups.indices.reversed()) {
            val group = groups[index]
            val value = group.toInt()
            if (value == 0) {
                if (builder.isNotEmpty()) pendingZero = true
                continue
            }
            val encoded = encodeGroup(group, style, colloquialTen && groups.size == 1)
            if (builder.isNotEmpty() && (pendingZero || group.length < 4 || group[0] == '0')) builder.append('零')
            builder.append(encoded)
            builder.append(groupUnits.getOrElse(index) { groupUnits.last() })
            pendingZero = false
        }
        return builder.toString()
    }

    private fun encodeGroup(group: String, style: ChineseNumberStyle, colloquialTen: Boolean): String {
        val digits = if (style == ChineseNumberStyle.LOWER) lowerDigits else upperDigits
        val smallUnits = if (style == ChineseNumberStyle.LOWER) charArrayOf('十', '百', '千') else charArrayOf('拾', '佰', '仟')
        val padded = group.padStart(4, '0')
        val builder = StringBuilder()
        var lastEmitted = -1
        for (position in 0..3) {
            val value = padded[position] - '0'
            if (value == 0) continue
            if (builder.isNotEmpty() && position - lastEmitted > 1) builder.append('零')
            val unitIndex = 3 - position
            if (!(colloquialTen && unitIndex == 1 && value == 1 && builder.isEmpty())) {
                builder.append(digits[value])
            }
            if (unitIndex > 0) builder.append(smallUnits[unitIndex - 1])
            lastEmitted = position
        }
        return builder.toString()
    }

    private fun money(integerText: String, decimals: String, negative: Boolean): String {
        val digits = upperDigits
        val padded = decimals.padEnd(2, '0')
        val jiao = padded[0] - '0'
        val fen = padded[1] - '0'
        val integerValue = integerText.trimStart('0')
        val integerPart = if (integerValue.isEmpty()) "" else encodeInteger(integerValue, ChineseNumberStyle.UPPER, colloquialTen = false) + "元"
        val builder = StringBuilder()
        if (jiao == 0 && fen == 0) {
            builder.append(integerPart.ifEmpty { "零元" }).append("整")
        } else {
            builder.append(integerPart)
            if (jiao != 0) {
                builder.append(digits[jiao]).append('角')
            } else if (fen != 0 && integerPart.isNotEmpty()) {
                builder.append('零')
            }
            if (fen != 0) builder.append(digits[fen]).append('分')
        }
        return (if (negative) "负" else "") + "人民币" + builder
    }

    // ------------------------------------------------------------------ decode

    private fun decode(input: String, style: ChineseNumberStyle): NumberChineseOutcome {
        var text = input.replace(" ", "")
        val negative = text.startsWith("负")
        if (negative) text = text.substring(1)
        val isMoney = style == ChineseNumberStyle.MONEY || text.startsWith("人民币") || text.contains('元') || text.contains('角') || text.contains('分')
        return if (isMoney) decodeMoney(text, negative) else decodeGeneral(text, negative)
    }

    private fun decodeGeneral(textIn: String, negative: Boolean): NumberChineseOutcome {
        var text = textIn.removePrefix("人民币").removeSuffix("整")
        val dotIndex = text.indexOf('点')
        val integerPart = if (dotIndex >= 0) text.substring(0, dotIndex) else text
        val decimalPart = if (dotIndex >= 0) text.substring(dotIndex + 1) else ""
        if (decimalPart.length > NumberChineseLimits.MAX_DECIMALS) return NumberChineseOutcome.Failure(NumberChineseError.INVALID_CHINESE)
        val integer = parseSections(integerPart) ?: return NumberChineseOutcome.Failure(NumberChineseError.INVALID_CHINESE)
        val decimals = StringBuilder()
        for (char in decimalPart) {
            val value = digitValue(char) ?: return NumberChineseOutcome.Failure(NumberChineseError.INVALID_CHINESE)
            decimals.append(value)
        }
        val body = integer.toString() + if (decimals.isNotEmpty()) ".$decimals" else ""
        return NumberChineseOutcome.Success((if (negative) "-" else "") + body)
    }

    private fun decodeMoney(textIn: String, negative: Boolean): NumberChineseOutcome {
        var text = textIn.removePrefix("人民币").removeSuffix("整")
        if (text.isEmpty()) return NumberChineseOutcome.Failure(NumberChineseError.INVALID_CHINESE)
        var yuanPart = ""
        var jiao = 0
        var fen = 0
        val yuanIndex = text.indexOf('元')
        if (yuanIndex >= 0) {
            yuanPart = text.substring(0, yuanIndex)
            text = text.substring(yuanIndex + 1)
        }
        val jiaoIndex = text.indexOf('角')
        if (jiaoIndex >= 0) {
            val digits = text.substring(0, jiaoIndex).trimStart('零')
            jiao = singleDigit(digits) ?: return NumberChineseOutcome.Failure(NumberChineseError.INVALID_CHINESE)
            text = text.substring(jiaoIndex + 1)
        }
        val fenIndex = text.indexOf('分')
        if (fenIndex >= 0) {
            val digits = text.substring(0, fenIndex).trimStart('零')
            fen = singleDigit(digits) ?: return NumberChineseOutcome.Failure(NumberChineseError.INVALID_CHINESE)
            text = text.substring(fenIndex + 1)
        }
        if (text.isNotEmpty()) return NumberChineseOutcome.Failure(NumberChineseError.INVALID_CHINESE)
        val yuan = if (yuanPart.isEmpty() || yuanPart == "零") BigInteger.ZERO else parseSections(yuanPart) ?: return NumberChineseOutcome.Failure(NumberChineseError.INVALID_CHINESE)
        val decimals = "$jiao$fen".trimEnd('0')
        val body = if (decimals.isEmpty()) yuan.toString() else "$yuan.$decimals"
        return NumberChineseOutcome.Success((if (negative) "-" else "") + body)
    }

    private fun singleDigit(text: String): Int? {
        if (text.isEmpty()) return null
        if (text.length == 1) return digitValue(text[0])
        return null
    }

    private fun parseSections(text: String): BigInteger? {
        if (text.isEmpty()) return BigInteger.ZERO
        if (text.all { digitValue(it) != null }) {
            return text.map { digitValue(it) }.joinToString("").toBigIntegerOrNull()
                ?.takeIf { it.toString().length <= NumberChineseLimits.MAX_INTEGER_DIGITS }
        }
        val normalized = text.flatMap { char ->
            val digit = digitValue(char)
            when {
                digit != null -> listOf(lowerDigits[digit])
                char == '拾' -> listOf('十')
                char == '佰' -> listOf('百')
                char == '仟' -> listOf('千')
                char == '萬' -> listOf('万')
                char == '億' -> listOf('亿')
                char == '兆' -> listOf('万', '亿')
                else -> listOf(char)
            }
        }.joinToString("")
        var result = BigInteger.ZERO
        var lastRank = 6
        var start = 0
        for (match in Regex("万亿亿|亿亿|万亿|亿|万").findAll(normalized)) {
            val rank = groupUnits.indexOf(match.value)
            if (rank >= lastRank || rank < 1) return null
            val group = parseSmallGroup(normalized.substring(start, match.range.first)) ?: return null
            result += group.toBigInteger() * BigInteger.TEN.pow(rank * 4)
            lastRank = rank
            start = match.range.last + 1
        }
        result += (parseSmallGroup(normalized.substring(start)) ?: return null).toBigInteger()
        if (result.toString().length > NumberChineseLimits.MAX_INTEGER_DIGITS) return null
        if (normalized != encodeInteger(result.toString(), ChineseNumberStyle.LOWER, true)
            && normalized != encodeInteger(result.toString(), ChineseNumberStyle.LOWER, false)) return null
        return result
    }

    private fun parseSmallGroup(text: String): Int? {
        var total = 0
        var number = 0
        for (char in text) {
            val digit = digitValue(char)
            if (digit != null) number = digit
            else {
                val factor = when (char) { '十' -> 10; '百' -> 100; '千' -> 1000; else -> return null }
                total += (if (number == 0 && factor == 10) 1 else number) * factor
                number = 0
            }
        }
        return (total + number).takeIf { it <= 9999 }
    }

    private fun digitValue(char: Char): Int? = when (char) {
        '零', '〇' -> 0
        '一', '壹' -> 1
        '二', '贰', '貳', '两', '兩' -> 2
        '三', '叁', '參' -> 3
        '四', '肆' -> 4
        '五', '伍' -> 5
        '六', '陆', '陸' -> 6
        '七', '柒' -> 7
        '八', '捌' -> 8
        '九', '玖' -> 9
        else -> null
    }
}
