package com.cangshuo.toolbox.feature.timestamp.domain

import androidx.annotation.StringRes
import com.cangshuo.toolbox.R
import java.time.ZoneId

enum class SupportedTimeZone(
    val zoneIdString: String?,
    @param:StringRes val labelResId: Int,
) {
    SYSTEM(null, R.string.timestamp_tz_system),
    UTC("UTC", R.string.timestamp_tz_utc),
    SHANGHAI("Asia/Shanghai", R.string.timestamp_tz_shanghai),
    TOKYO("Asia/Tokyo", R.string.timestamp_tz_tokyo),
    LONDON("Europe/London", R.string.timestamp_tz_london),
    NEW_YORK("America/New_York", R.string.timestamp_tz_new_york),
    LOS_ANGELES("America/Los_Angeles", R.string.timestamp_tz_los_angeles);

    fun toZoneId(): ZoneId = if (zoneIdString != null) ZoneId.of(zoneIdString) else ZoneId.systemDefault()
}
