package com.cangshuo.toolbox

import android.app.Application

class ToolboxApplication : Application() {
    val container: ToolboxAppContainer by lazy { ToolboxAppContainer(this) }
}
