package com.cangshuo.toolbox.feature.uuid.data

import com.cangshuo.toolbox.feature.uuid.domain.UuidConfig
import com.cangshuo.toolbox.feature.uuid.domain.UuidRepository
import java.util.Locale
import java.util.UUID

class LocalUuidRepository : UuidRepository {

    override fun generateRaw(count: Int): List<String> {
        val clampedCount = count.coerceIn(MIN_COUNT, MAX_COUNT)
        return List(clampedCount) { UUID.randomUUID().toString() }
    }

    override fun format(raw: String, config: UuidConfig): String {
        var value = if (config.hasHyphens) raw else raw.replace("-", "")
        value = if (config.isUppercase) {
            value.uppercase(Locale.ROOT)
        } else {
            value.lowercase(Locale.ROOT)
        }
        return if (config.isBraced) "{$value}" else value
    }

    private companion object {
        const val MIN_COUNT = 1
        const val MAX_COUNT = 100
    }
}
