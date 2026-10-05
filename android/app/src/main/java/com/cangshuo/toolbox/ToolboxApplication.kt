package com.cangshuo.toolbox

import android.app.Application

class ToolboxApplication : Application() {
    val container: ToolboxAppContainer by lazy { ToolboxAppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        com.cangshuo.toolbox.feature.diagnostics.data.installLocalCrashMonitor(this)
    }
}
