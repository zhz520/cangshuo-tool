package com.cangshuo.toolbox.core.model

/** Stable category codes shared with the tool catalog; never persist enum ordinals. */
enum class ToolCategory(val code: String) {
    CALC("CALC"),
    CONVERT("CONVERT"),
    TEXT("TEXT"),
    DEV("DEV"),
    QR("QR"),
    IMAGE("IMAGE"),
    PDF("PDF"),
    DEVICE("DEVICE"),
    SENSOR("SENSOR"),
    NETWORK("NETWORK"),
    LIFE("LIFE"),
    AI("AI"),
    OTHER("OTHER"),
    ;

    companion object {
        /** Unknown codes remain unknown so callers can handle newer catalogs safely. */
        fun fromCode(code: String): ToolCategory? = entries.firstOrNull { it.code == code }
    }
}
