package com.cangshuo.toolbox.feature.converter.data

import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.converter.domain.ConversionType
import com.cangshuo.toolbox.feature.converter.domain.UnitDef
import java.math.BigDecimal

/** Static unit definitions for all 12 conversion categories. */
internal object UnitData {

    fun unitsFor(type: ConversionType): List<UnitDef> = units.getValue(type)

    private val units: Map<ConversionType, List<UnitDef>> = mapOf(
        // ── LENGTH (base: meter) ────────────────────────────────────────
        ConversionType.LENGTH to listOf(
            UnitDef("millimeter", "mm", R.string.converter_unit_millimeter, BigDecimal("0.001")),
            UnitDef("centimeter", "cm", R.string.converter_unit_centimeter, BigDecimal("0.01")),
            UnitDef("meter", "m", R.string.converter_unit_meter, BigDecimal("1")),
            UnitDef("kilometer", "km", R.string.converter_unit_kilometer, BigDecimal("1000")),
            UnitDef("inch", "in", R.string.converter_unit_inch, BigDecimal("0.0254")),
            UnitDef("foot", "ft", R.string.converter_unit_foot, BigDecimal("0.3048")),
            UnitDef("yard", "yd", R.string.converter_unit_yard, BigDecimal("0.9144")),
            UnitDef("mile", "mi", R.string.converter_unit_mile, BigDecimal("1609.344")),
            UnitDef("nautical_mile", "nmi", R.string.converter_unit_nautical_mile, BigDecimal("1852")),
        ),
        // ── AREA (base: m²) ─────────────────────────────────────────────
        ConversionType.AREA to listOf(
            UnitDef("sq_millimeter", "mm²", R.string.converter_unit_sq_millimeter, BigDecimal("0.000001")),
            UnitDef("sq_centimeter", "cm²", R.string.converter_unit_sq_centimeter, BigDecimal("0.0001")),
            UnitDef("sq_meter", "m²", R.string.converter_unit_sq_meter, BigDecimal("1")),
            UnitDef("sq_kilometer", "km²", R.string.converter_unit_sq_kilometer, BigDecimal("1000000")),
            UnitDef("hectare", "ha", R.string.converter_unit_hectare, BigDecimal("10000")),
            UnitDef("acre", "ac", R.string.converter_unit_acre, BigDecimal("4046.8564224")),
            UnitDef("sq_inch", "in²", R.string.converter_unit_sq_inch, BigDecimal("0.00064516")),
            UnitDef("sq_foot", "ft²", R.string.converter_unit_sq_foot, BigDecimal("0.09290304")),
            UnitDef("sq_yard", "yd²", R.string.converter_unit_sq_yard, BigDecimal("0.83612736")),
            UnitDef("sq_mile", "mi²", R.string.converter_unit_sq_mile, BigDecimal("2589988.110336")),
        ),
        // ── VOLUME (base: liter) ────────────────────────────────────────
        ConversionType.VOLUME to listOf(
            UnitDef("milliliter", "mL", R.string.converter_unit_milliliter, BigDecimal("0.001")),
            UnitDef("liter", "L", R.string.converter_unit_liter, BigDecimal("1")),
            UnitDef("cubic_meter", "m³", R.string.converter_unit_cubic_meter, BigDecimal("1000")),
            UnitDef("us_gallon", "gal", R.string.converter_unit_us_gallon, BigDecimal("3.785411784")),
            UnitDef("us_quart", "qt", R.string.converter_unit_us_quart, BigDecimal("0.946352946")),
            UnitDef("us_pint", "pt", R.string.converter_unit_us_pint, BigDecimal("0.473176473")),
            UnitDef("us_cup", "cup", R.string.converter_unit_us_cup, BigDecimal("0.2365882365")),
            UnitDef("us_fl_oz", "fl oz", R.string.converter_unit_us_fl_oz, BigDecimal("0.0295735295625")),
            UnitDef("tablespoon", "tbsp", R.string.converter_unit_tablespoon, BigDecimal("0.0147867647813")),
            UnitDef("teaspoon", "tsp", R.string.converter_unit_teaspoon, BigDecimal("0.00492892159375")),
        ),
        // ── MASS (base: kilogram) ───────────────────────────────────────
        ConversionType.MASS to listOf(
            UnitDef("milligram", "mg", R.string.converter_unit_milligram, BigDecimal("0.000001")),
            UnitDef("gram", "g", R.string.converter_unit_gram, BigDecimal("0.001")),
            UnitDef("kilogram", "kg", R.string.converter_unit_kilogram, BigDecimal("1")),
            UnitDef("metric_ton", "t", R.string.converter_unit_metric_ton, BigDecimal("1000")),
            UnitDef("pound", "lb", R.string.converter_unit_pound, BigDecimal("0.45359237")),
            UnitDef("ounce", "oz", R.string.converter_unit_ounce, BigDecimal("0.028349523125")),
            UnitDef("stone", "st", R.string.converter_unit_stone, BigDecimal("6.35029318")),
        ),
        // ── TEMPERATURE (special formulas; factor is unused) ────────────
        ConversionType.TEMPERATURE to listOf(
            UnitDef("celsius", "°C", R.string.converter_unit_celsius, BigDecimal.ONE),
            UnitDef("fahrenheit", "°F", R.string.converter_unit_fahrenheit, BigDecimal.ONE),
            UnitDef("kelvin", "K", R.string.converter_unit_kelvin, BigDecimal.ONE),
        ),
        // ── SPEED (base: m/s) ───────────────────────────────────────────
        ConversionType.SPEED to listOf(
            UnitDef("meter_per_second", "m/s", R.string.converter_unit_meter_per_second, BigDecimal("1")),
            UnitDef("km_per_hour", "km/h", R.string.converter_unit_km_per_hour, BigDecimal("0.27777777777778")),
            UnitDef("mile_per_hour", "mph", R.string.converter_unit_mile_per_hour, BigDecimal("0.44704")),
            UnitDef("knot", "kn", R.string.converter_unit_knot, BigDecimal("0.51444444444444")),
            UnitDef("foot_per_second", "ft/s", R.string.converter_unit_foot_per_second, BigDecimal("0.3048")),
        ),
        // ── DATA (base: byte) ───────────────────────────────────────────
        ConversionType.DATA to listOf(
            UnitDef("bit", "bit", R.string.converter_unit_bit, BigDecimal("0.125")),
            UnitDef("byte", "B", R.string.converter_unit_byte, BigDecimal("1")),
            UnitDef("kilobyte", "KB", R.string.converter_unit_kilobyte, BigDecimal("1000")),
            UnitDef("megabyte", "MB", R.string.converter_unit_megabyte, BigDecimal("1000000")),
            UnitDef("gigabyte", "GB", R.string.converter_unit_gigabyte, BigDecimal("1000000000")),
            UnitDef("terabyte", "TB", R.string.converter_unit_terabyte, BigDecimal("1000000000000")),
            UnitDef("petabyte", "PB", R.string.converter_unit_petabyte, BigDecimal("1000000000000000")),
            UnitDef("kibibyte", "KiB", R.string.converter_unit_kibibyte, BigDecimal("1024")),
            UnitDef("mebibyte", "MiB", R.string.converter_unit_mebibyte, BigDecimal("1048576")),
            UnitDef("gibibyte", "GiB", R.string.converter_unit_gibibyte, BigDecimal("1073741824")),
            UnitDef("tebibyte", "TiB", R.string.converter_unit_tebibyte, BigDecimal("1099511627776")),
        ),
        // ── TIME (base: second) ─────────────────────────────────────────
        ConversionType.TIME to listOf(
            UnitDef("millisecond", "ms", R.string.converter_unit_millisecond, BigDecimal("0.001")),
            UnitDef("second", "s", R.string.converter_unit_second, BigDecimal("1")),
            UnitDef("minute", "min", R.string.converter_unit_minute, BigDecimal("60")),
            UnitDef("hour", "h", R.string.converter_unit_hour, BigDecimal("3600")),
            UnitDef("day", "d", R.string.converter_unit_day, BigDecimal("86400")),
            UnitDef("week", "wk", R.string.converter_unit_week, BigDecimal("604800")),
            UnitDef("month_30", "mo", R.string.converter_unit_month, BigDecimal("2592000")),
            UnitDef("year_365", "yr", R.string.converter_unit_year, BigDecimal("31536000")),
        ),
        // ── PRESSURE (base: pascal) ─────────────────────────────────────
        ConversionType.PRESSURE to listOf(
            UnitDef("pascal", "Pa", R.string.converter_unit_pascal, BigDecimal("1")),
            UnitDef("kilopascal", "kPa", R.string.converter_unit_kilopascal, BigDecimal("1000")),
            UnitDef("megapascal", "MPa", R.string.converter_unit_megapascal, BigDecimal("1000000")),
            UnitDef("bar", "bar", R.string.converter_unit_bar, BigDecimal("100000")),
            UnitDef("atmosphere", "atm", R.string.converter_unit_atmosphere, BigDecimal("101325")),
            UnitDef("psi", "psi", R.string.converter_unit_psi, BigDecimal("6894.757293168")),
            UnitDef("mmhg", "mmHg", R.string.converter_unit_mmhg, BigDecimal("133.322387415")),
        ),
        // ── POWER (base: watt) ──────────────────────────────────────────
        ConversionType.POWER to listOf(
            UnitDef("watt", "W", R.string.converter_unit_watt, BigDecimal("1")),
            UnitDef("kilowatt", "kW", R.string.converter_unit_kilowatt, BigDecimal("1000")),
            UnitDef("megawatt", "MW", R.string.converter_unit_megawatt, BigDecimal("1000000")),
            UnitDef("horsepower", "hp", R.string.converter_unit_horsepower, BigDecimal("745.69987158227")),
            UnitDef("btu_per_hour", "BTU/h", R.string.converter_unit_btu_per_hour, BigDecimal("0.29307107017222")),
        ),
        // ── ENERGY (base: joule) ────────────────────────────────────────
        ConversionType.ENERGY to listOf(
            UnitDef("joule", "J", R.string.converter_unit_joule, BigDecimal("1")),
            UnitDef("kilojoule", "kJ", R.string.converter_unit_kilojoule, BigDecimal("1000")),
            UnitDef("megajoule", "MJ", R.string.converter_unit_megajoule, BigDecimal("1000000")),
            UnitDef("calorie", "cal", R.string.converter_unit_calorie, BigDecimal("4.184")),
            UnitDef("kilocalorie", "kcal", R.string.converter_unit_kilocalorie, BigDecimal("4184")),
            UnitDef("kilowatt_hour", "kWh", R.string.converter_unit_kilowatt_hour, BigDecimal("3600000")),
            UnitDef("btu", "BTU", R.string.converter_unit_btu, BigDecimal("1055.05585262")),
        ),
        // ── ANGLE (base: degree) ────────────────────────────────────────
        ConversionType.ANGLE to listOf(
            UnitDef("degree", "°", R.string.converter_unit_degree, BigDecimal("1")),
            UnitDef("radian", "rad", R.string.converter_unit_radian, BigDecimal("57.29577951308232")),
            UnitDef("gradian", "gon", R.string.converter_unit_gradian, BigDecimal("0.9")),
            UnitDef("turn", "tr", R.string.converter_unit_turn, BigDecimal("360")),
            UnitDef("arcminute", "′", R.string.converter_unit_arcminute, BigDecimal("0.01666666666667")),
            UnitDef("arcsecond", "″", R.string.converter_unit_arcsecond, BigDecimal("0.00027777777778")),
        ),
    )
}
