package com.cangshuo.toolbox.feature.home.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory

@StringRes
fun ToolCategory.labelResource(): Int = when (this) {
    ToolCategory.CALC -> R.string.category_calc
    ToolCategory.CONVERT -> R.string.category_convert
    ToolCategory.TEXT -> R.string.category_text
    ToolCategory.DEV -> R.string.category_dev
    ToolCategory.QR -> R.string.category_qr
    ToolCategory.IMAGE -> R.string.category_image
    ToolCategory.PDF -> R.string.category_pdf
    ToolCategory.DEVICE -> R.string.category_device
    ToolCategory.SENSOR -> R.string.category_sensor
    ToolCategory.NETWORK -> R.string.category_network
    ToolCategory.LIFE -> R.string.category_life
    ToolCategory.AI -> R.string.category_ai
    ToolCategory.OTHER -> R.string.category_other
}

/** Category representative icon, used by the home "explore" cards and the library. */
@DrawableRes
fun ToolCategory.iconResource(): Int = when (this) {
    ToolCategory.CALC -> R.drawable.ic_calculator
    ToolCategory.CONVERT -> R.drawable.ic_converter
    ToolCategory.TEXT -> R.drawable.ic_text
    ToolCategory.DEV -> R.drawable.ic_hash
    ToolCategory.QR -> R.drawable.ic_qr
    ToolCategory.IMAGE -> R.drawable.ic_image_compress
    ToolCategory.PDF -> R.drawable.ic_pdf
    ToolCategory.DEVICE -> R.drawable.ic_device_info
    ToolCategory.SENSOR -> R.drawable.ic_sensor_info
    ToolCategory.NETWORK -> R.drawable.ic_http_status
    ToolCategory.LIFE -> R.drawable.ic_timestamp
    ToolCategory.AI -> R.drawable.ic_chat
    ToolCategory.OTHER -> R.drawable.ic_tools
}

@StringRes
fun HomeTab.labelResource(): Int = when (this) {
    HomeTab.HOME -> R.string.nav_home
    HomeTab.TOOLS -> R.string.nav_tools
    HomeTab.FAVORITES -> R.string.nav_favorites
    HomeTab.PROFILE -> R.string.nav_profile
}

@DrawableRes
fun HomeTab.iconResource(): Int = when (this) {
    HomeTab.HOME -> R.drawable.ic_home
    HomeTab.TOOLS -> R.drawable.ic_tools
    HomeTab.FAVORITES -> R.drawable.ic_star
    HomeTab.PROFILE -> R.drawable.ic_person
}

@StringRes
fun HomeMessage.labelResource(): Int = when (this) {
    HomeMessage.NOT_FOUND -> R.string.tool_not_found
    HomeMessage.DISABLED -> R.string.tool_disabled
    HomeMessage.MAINTENANCE -> R.string.tool_maintenance
    HomeMessage.UNSUPPORTED -> R.string.tool_unsupported
    HomeMessage.LOGIN_REQUIRED -> R.string.tool_login_required
    HomeMessage.PERMISSION_REQUIRED -> R.string.tool_permission_required
    HomeMessage.OPEN_ERROR -> R.string.tool_open_error
}

/** Symbolic names map only to compiled resources; unknown names use the shared fallback. */
@DrawableRes
fun toolIconResource(name: String?): Int = when (name) {
    "calculator" -> R.drawable.ic_calculator
    "converter" -> R.drawable.ic_converter
    "timestamp" -> R.drawable.ic_timestamp
    "uuid" -> R.drawable.ic_uuid
    "http_status" -> R.drawable.ic_http_status
    "ping" -> R.drawable.ic_ping
    "level" -> R.drawable.ic_level
    "compass" -> R.drawable.ic_compass
    "sensor_info" -> R.drawable.ic_sensor_info
    "light_meter" -> R.drawable.ic_light_meter
    "battery_info" -> R.drawable.ic_battery_info
    "storage_info" -> R.drawable.ic_storage_info
    "device_info" -> R.drawable.ic_device_info
    "base64" -> R.drawable.ic_base64
    "url_codec" -> R.drawable.ic_url_codec
    "hash" -> R.drawable.ic_hash
    "radix" -> R.drawable.ic_radix
    "bmi" -> R.drawable.ic_bmi
    "compound_interest" -> R.drawable.ic_interest
    "mortgage" -> R.drawable.ic_mortgage
    "fuel" -> R.drawable.ic_fuel
    "date_calc" -> R.drawable.ic_date
    "color_converter" -> R.drawable.ic_color
    "password_gen" -> R.drawable.ic_password
    "text_counter" -> R.drawable.ic_text_count
    "url_extract" -> R.drawable.ic_url_extract
    "kinship" -> R.drawable.ic_kinship
    "number_chinese" -> R.drawable.ic_number_chinese
    "char_convert" -> R.drawable.ic_char_convert
    "text_similarity" -> R.drawable.ic_similarity
    "morse" -> R.drawable.ic_morse
    "json" -> R.drawable.ic_json
    "text" -> R.drawable.ic_text
    "qr" -> R.drawable.ic_qr
    "pdf" -> R.drawable.ic_pdf
    "image_compress" -> R.drawable.ic_image_compress
    "image" -> R.drawable.ic_image_compress
    "toolbox" -> R.drawable.ic_toolbox
    "tools" -> R.drawable.ic_tools
    else -> R.drawable.ic_tools
}
