package com.cangshuo.toolbox.core.model

enum class ToolMode {
    LOCAL,
    SERVER,
    HYBRID,
    /** Hosted on the official site (`/tools/<code>/`) and opened in the app's WebView container. */
    WEB,
}
