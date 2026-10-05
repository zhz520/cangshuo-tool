package com.cangshuo.toolbox.feature.deviceinfo.data

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import com.cangshuo.toolbox.BuildConfig
import com.cangshuo.toolbox.feature.deviceinfo.domain.*
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidDeviceInfoRepository(context: Context) : DeviceInfoRepository {
    private val context=context.applicationContext
    override suspend fun read(): DeviceSnapshot=withContext(Dispatchers.IO) {
        val manager=context.getSystemService(ActivityManager::class.java)
        val memory=runCatching { ActivityManager.MemoryInfo().also { manager?.getMemoryInfo(it) } }.getOrNull()
        val available=memory?.takeIf { it.totalMem>0 && it.availMem in 0..it.totalMem }?.availMem
        val mode=runCatching { context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.mode }.getOrNull()
        val soc=if(Build.VERSION.SDK_INT>=31) listOf(Build.SOC_MANUFACTURER,Build.SOC_MODEL)
            .mapNotNull(DeviceInfoFormat::text).joinToString(" / ") else null
        DeviceSnapshot(mapOf(
            DeviceField.MANUFACTURER to Build.MANUFACTURER,DeviceField.BRAND to Build.BRAND,DeviceField.MODEL to Build.MODEL,
            DeviceField.DEVICE to Build.DEVICE,DeviceField.PRODUCT to Build.PRODUCT,DeviceField.HARDWARE to Build.HARDWARE,
            DeviceField.SOC to soc,DeviceField.ANDROID to Build.VERSION.RELEASE,DeviceField.API to Build.VERSION.SDK_INT.toString(),
            DeviceField.SECURITY_PATCH to Build.VERSION.SECURITY_PATCH,DeviceField.BUILD to Build.DISPLAY,
            DeviceField.ABI to Build.SUPPORTED_ABIS?.take(16)?.joinToString(", "),
            DeviceField.PROCESSORS to Runtime.getRuntime().availableProcessors().takeIf { it>0 }?.toString(),
            DeviceField.TOTAL_RAM to DeviceInfoFormat.bytes(memory?.totalMem?.takeIf { it>0 }),
            DeviceField.AVAILABLE_RAM to DeviceInfoFormat.bytes(available),
            DeviceField.UPTIME to DeviceInfoFormat.uptime(SystemClock.elapsedRealtime()),
            DeviceField.PANEL to mode?.takeIf { it.physicalWidth>0 && it.physicalHeight>0 }?.let { "${it.physicalWidth} × ${it.physicalHeight} px" },
            DeviceField.REFRESH_RATE to mode?.refreshRate?.takeIf { it.isFinite() && it>0 }?.let { String.format(Locale.ROOT,"%.2f Hz",it) },
            DeviceField.DENSITY to context.resources.displayMetrics.densityDpi.takeIf { it>0 }?.let { "$it dpi" },
            DeviceField.GLES to runCatching { manager?.deviceConfigurationInfo?.takeIf { it.reqGlEsVersion>0 }?.glEsVersion }.getOrNull(),
            DeviceField.APP to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        ))
    }
}
