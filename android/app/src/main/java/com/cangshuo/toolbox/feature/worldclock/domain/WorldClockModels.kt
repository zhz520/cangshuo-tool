package com.cangshuo.toolbox.feature.worldclock.domain

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.zone.ZoneRules
import java.util.Locale

/** Reads only the device clock and the time-zone database shipped with the OS. */
interface WorldClockRepository {
    fun now(): Instant
    fun systemZoneId(): String
    fun availableZoneIds(): Set<String>
}

data class WorldClockZone(
    val id: String,
    val city: String,
    val region: String,
    val searchText: String,
)

enum class WorldClockHourFormat { HOUR_24, HOUR_12 }

enum class WorldClockDstState {
    FIXED_OFFSET,
    STANDARD,
    DAYLIGHT,
}

data class WorldClockTransition(
    val localDateTime: String,
    val newOffset: String,
)

data class WorldClockReading(
    val zoneId: String,
    val city: String,
    val region: String,
    val time: String,
    val date: String,
    val weekday: String,
    val utcOffset: String,
    val dstState: WorldClockDstState,
    val nextTransition: WorldClockTransition?,
    /** Calendar days relative to the device time zone: -1 previous day, 0 same day, 1 next day. */
    val dayDelta: Long,
)

class WorldClockUseCases(private val repository: WorldClockRepository) {
    fun now(): Instant = repository.now()

    fun systemZoneId(): String = repository.systemZoneId()

    fun zoneOptions(locale: Locale): List<WorldClockZone> {
        val ids = buildSet {
            addAll(repository.availableZoneIds())
            add(repository.systemZoneId())
            addAll(WorldClockPolicy.COMMON_ZONE_IDS)
        }.mapNotNull { id -> WorldClockPolicy.safeZone(id)?.let { id } }

        val commonRank = WorldClockPolicy.COMMON_ZONE_IDS.withIndex().associate { it.value to it.index }
        return ids.distinct().mapNotNull { id ->
            runCatching { WorldClockPolicy.option(id, locale) }.getOrNull()
        }
            .sortedWith(
                compareBy<WorldClockZone> { commonRank[it.id] ?: Int.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.city }
                    .thenBy { it.id },
            )
    }

    fun filterZones(zones: List<WorldClockZone>, query: String): List<WorldClockZone> =
        WorldClockPolicy.filter(zones, query)

    fun readings(
        zoneIds: List<String>,
        instant: Instant,
        referenceZoneId: String,
        locale: Locale,
        hourFormat: WorldClockHourFormat,
    ): List<WorldClockReading> {
        val reference = WorldClockPolicy.safeZone(referenceZoneId) ?: ZoneOffset.UTC
        val referenceDate = instant.atZone(reference).toLocalDate()
        return zoneIds.distinct().mapNotNull { rawId ->
            val id = WorldClockPolicy.validatedId(rawId) ?: return@mapNotNull null
            runCatching {
                WorldClockPolicy.reading(id, instant, referenceDate, locale, hourFormat)
            }.getOrNull()
        }
    }
}

object WorldClockPolicy {
    const val MAX_TOTAL_ZONES = 24
    const val MAX_QUERY_LENGTH = 80
    const val TICK_INTERVAL_MS = 1_000L

    val DEFAULT_ZONE_IDS = listOf("Asia/Shanghai", "Europe/London", "America/New_York")

    val COMMON_ZONE_IDS = listOf(
        "Asia/Shanghai",
        "Asia/Tokyo",
        "Asia/Singapore",
        "Asia/Hong_Kong",
        "Asia/Dubai",
        "Asia/Kolkata",
        "Europe/London",
        "Europe/Paris",
        "Europe/Berlin",
        "America/New_York",
        "America/Chicago",
        "America/Los_Angeles",
        "America/Sao_Paulo",
        "Australia/Sydney",
        "Pacific/Auckland",
        "Africa/Cairo",
    )

    private val aliases = mapOf(
        "Asia/Shanghai" to "北京 上海 中国 beijing china prc",
        "Asia/Chongqing" to "重庆 中国 chongqing china",
        "Asia/Harbin" to "哈尔滨 中国 harbin china",
        "Asia/Urumqi" to "乌鲁木齐 新疆 中国 urumqi xinjiang china",
        "Asia/Hong_Kong" to "香港 中国 hong kong hk china",
        "Asia/Macau" to "澳门 中国 macau macao china",
        "Asia/Taipei" to "台北 台湾 taipei taiwan",
        "Asia/Tokyo" to "东京 日本 tokyo japan",
        "Asia/Seoul" to "首尔 韩国 seoul korea",
        "Asia/Singapore" to "新加坡 singapore",
        "Asia/Bangkok" to "曼谷 泰国 bangkok thailand",
        "Asia/Jakarta" to "雅加达 印度尼西亚 jakarta indonesia",
        "Asia/Manila" to "马尼拉 菲律宾 manila philippines",
        "Asia/Kuala_Lumpur" to "吉隆坡 马来西亚 kuala lumpur malaysia",
        "Asia/Dubai" to "迪拜 阿联酋 dubai uae",
        "Asia/Kolkata" to "印度 新德里 孟买 kolkata india delhi mumbai",
        "Europe/London" to "伦敦 英国 london uk britain",
        "Europe/Paris" to "巴黎 法国 paris france",
        "Europe/Berlin" to "柏林 德国 berlin germany",
        "Europe/Madrid" to "马德里 西班牙 madrid spain",
        "Europe/Rome" to "罗马 意大利 rome italy",
        "Europe/Moscow" to "莫斯科 俄罗斯 moscow russia",
        "America/New_York" to "纽约 华盛顿 美国东部 new york washington us eastern",
        "America/Chicago" to "芝加哥 美国中部 chicago us central",
        "America/Denver" to "丹佛 美国山地 denver us mountain",
        "America/Phoenix" to "凤凰城 亚利桑那 phoenix arizona",
        "America/Los_Angeles" to "洛杉矶 旧金山 西雅图 美国西部 los angeles san francisco seattle us pacific",
        "America/Anchorage" to "安克雷奇 阿拉斯加 anchorage alaska",
        "Pacific/Honolulu" to "檀香山 夏威夷 honolulu hawaii",
        "America/Toronto" to "多伦多 加拿大 toronto canada",
        "America/Vancouver" to "温哥华 加拿大 vancouver canada",
        "America/Mexico_City" to "墨西哥城 mexico city",
        "America/Sao_Paulo" to "圣保罗 巴西 sao paulo brazil",
        "America/Argentina/Buenos_Aires" to "布宜诺斯艾利斯 阿根廷 buenos aires argentina",
        "Africa/Cairo" to "开罗 埃及 cairo egypt",
        "Africa/Johannesburg" to "约翰内斯堡 南非 johannesburg south africa",
        "Africa/Nairobi" to "内罗毕 肯尼亚 nairobi kenya",
        "Australia/Sydney" to "悉尼 澳大利亚 sydney australia",
        "Australia/Melbourne" to "墨尔本 澳大利亚 melbourne australia",
        "Australia/Perth" to "珀斯 澳大利亚 perth australia",
        "Pacific/Auckland" to "奥克兰 新西兰 auckland new zealand",
        "Pacific/Fiji" to "斐济 fiji",
    )

    fun validatedId(raw: String): String? {
        val id = raw.trim()
        if (id.isEmpty() || id.length > 128) return null
        return runCatching { ZoneId.of(id).id }.getOrNull()
    }

    fun safeZone(id: String): ZoneId? = validatedId(id)?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    fun sanitize(ids: Collection<String>, systemZoneId: String): List<String> {
        val system = validatedId(systemZoneId)
        val seen = mutableSetOf<String>()
        val result = ArrayList<String>(MAX_TOTAL_ZONES - 1)
        ids.forEach { raw ->
            val id = validatedId(raw) ?: return@forEach
            if (id == system || !seen.add(id)) return@forEach
            if (result.size < MAX_TOTAL_ZONES - 1) result.add(id)
        }
        return result
    }

    fun encode(ids: Collection<String>): ArrayList<String> = ArrayList(ids.take(MAX_TOTAL_ZONES - 1))

    fun decode(values: Collection<String>): List<String> = values.take(MAX_TOTAL_ZONES - 1)

    fun option(id: String, locale: Locale): WorldClockZone {
        val normalizedId = validatedId(id) ?: id
        val parts = normalizedId.split('/')
        val city = (parts.lastOrNull().takeUnless { it.isNullOrBlank() } ?: normalizedId)
            .replace('_', ' ')
        val region = if (parts.size > 1) parts.dropLast(1).joinToString(" / ").replace('_', ' ') else ""
        val localized = safeZone(normalizedId)
            ?.getDisplayName(TextStyle.FULL, locale)
            .orEmpty()
        val extra = aliases.entries
            .firstOrNull { (aliasId) -> aliasId == normalizedId }
            ?.value
            .orEmpty()
        return WorldClockZone(
            id = normalizedId,
            city = city,
            region = region,
            searchText = normalize("$normalizedId $city $region $localized $extra"),
        )
    }

    fun filter(zones: List<WorldClockZone>, query: String): List<WorldClockZone> {
        val terms = normalize(query).split(' ').filter(String::isNotEmpty)
        if (terms.isEmpty()) return zones
        return zones.filter { zone -> terms.all { it in zone.searchText } }
    }

    fun reading(
        zoneId: String,
        instant: Instant,
        referenceDate: java.time.LocalDate,
        locale: Locale,
        hourFormat: WorldClockHourFormat,
    ): WorldClockReading {
        val zone = ZoneId.of(validatedId(zoneId) ?: error("Invalid zone"))
        val zoned = instant.atZone(zone)
        val rules = zone.rules
        val option = option(zone.id, locale)
        val timePattern = if (hourFormat == WorldClockHourFormat.HOUR_24) "HH:mm:ss" else "hh:mm:ss a"
        val timeFormatter = DateTimeFormatter.ofPattern(timePattern, locale)
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE", locale)
        val transitionFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
        val transition = rules.nextTransition(instant)?.let { next ->
            WorldClockTransition(
                localDateTime = transitionFormatter.format(next.instant.atZone(zone)),
                newOffset = formatOffset(next.offsetAfter.totalSeconds),
            )
        }
        return WorldClockReading(
            zoneId = zone.id,
            city = option.city,
            region = option.region,
            time = timeFormatter.format(zoned),
            date = dateFormatter.format(zoned),
            weekday = weekdayFormatter.format(zoned),
            utcOffset = formatOffset(rules.getOffset(instant).totalSeconds),
            dstState = dstState(rules, instant),
            nextTransition = transition,
            dayDelta = ChronoUnit.DAYS.between(referenceDate, zoned.toLocalDate()),
        )
    }

    fun formatOffset(totalSeconds: Int): String {
        val sign = if (totalSeconds < 0) '-' else '+'
        val absolute = kotlin.math.abs(totalSeconds)
        val hours = absolute / 3_600
        val minutes = absolute % 3_600 / 60
        val seconds = absolute % 60
        val base = "UTC$sign%02d:%02d".format(Locale.ROOT, hours, minutes)
        return if (seconds == 0) base else "$base:%02d".format(Locale.ROOT, seconds)
    }

    private fun dstState(rules: ZoneRules, instant: Instant): WorldClockDstState = when {
        rules.transitionRules.isEmpty() -> WorldClockDstState.FIXED_OFFSET
        rules.isDaylightSavings(instant) -> WorldClockDstState.DAYLIGHT
        else -> WorldClockDstState.STANDARD
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s_/.,:;()\\[\\]-]+"), " ")
        .trim()
}
