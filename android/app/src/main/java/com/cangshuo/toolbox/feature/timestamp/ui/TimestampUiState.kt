package com.cangshuo.toolbox.feature.timestamp.ui

import androidx.annotation.StringRes
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.timestamp.domain.SupportedTimeZone
import com.cangshuo.toolbox.feature.timestamp.domain.TimestampUnit

enum class TimestampDateError(@param:StringRes val messageResId: Int) {
    INVALID_FORMAT(R.string.timestamp_date_error),
    INVALID_DATE(R.string.timestamp_date_error_invalid_date),
    OUT_OF_RANGE(R.string.timestamp_date_error_range),
}

data class TimestampUiState(
    // Current time
    val currentSeconds: Long = 0L,
    val currentMillis: Long = 0L,
    val currentFormatted: String = "",
    val isLive: Boolean = true,

    // Timestamp to Date
    val tsInput: String = "",
    val tsUnit: TimestampUnit = TimestampUnit.SECONDS,
    val tsTimeZone: SupportedTimeZone = SupportedTimeZone.SYSTEM,
    val tsResultFormatted: String = "",
    val tsResultIso: String = "",
    val tsIsError: Boolean = false,

    // Date to Timestamp
    val dateInput: String = "",
    val dateTimeZone: SupportedTimeZone = SupportedTimeZone.SYSTEM,
    val dateResultSeconds: String = "",
    val dateResultMillis: String = "",
    val dateError: TimestampDateError? = null,
)
