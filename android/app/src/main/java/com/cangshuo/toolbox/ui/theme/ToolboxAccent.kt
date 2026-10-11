package com.cangshuo.toolbox.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * User-selectable accent palette. Blue stays the default; every other entry keeps a similar
 * lightness so white button labels and dark-theme text stay readable.
 */
enum class ToolboxAccent(val storageKey: String, val light: Color, val dark: Color) {
    BLUE("BLUE", Color(0xFF1677FF), Color(0xFF4C9BFF)),
    TEAL("TEAL", Color(0xFF0E9384), Color(0xFF2CC5B4)),
    VIOLET("VIOLET", Color(0xFF6E56CF), Color(0xFFA08CFF)),
    ORANGE("ORANGE", Color(0xFFD9480F), Color(0xFFFF9A52)),
    PINK("PINK", Color(0xFFD6336C), Color(0xFFF783AC)),
    BLACK("BLACK", Color(0xFF171717), Color(0xFFF5F5F5));

    companion object {
        fun of(key: String?): ToolboxAccent = entries.firstOrNull { it.storageKey == key } ?: BLUE
    }
}
