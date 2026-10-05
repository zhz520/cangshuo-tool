package com.cangshuo.toolbox.feature.deviceinfo.domain

import java.util.Locale

enum class DeviceField { MANUFACTURER, BRAND, MODEL, DEVICE, PRODUCT, HARDWARE, SOC, ANDROID, API,
    SECURITY_PATCH, BUILD, ABI, PROCESSORS, TOTAL_RAM, AVAILABLE_RAM, UPTIME, PANEL, REFRESH_RATE, DENSITY, GLES, APP }
data class DeviceSnapshot(val values: Map<DeviceField,String?>) {
    override fun toString()="DeviceSnapshot[redacted]"
}
interface DeviceInfoRepository { suspend fun read(): DeviceSnapshot }
class ReadDeviceInfoUseCase(private val repository: DeviceInfoRepository) {
    suspend operator fun invoke()=repository.read().let { snapshot ->
        DeviceSnapshot(snapshot.values.mapValues { DeviceInfoFormat.text(it.value) })
    }
}
object DeviceInfoFormat {
    fun text(value: String?): String? {
        val cleaned=value?.map { if(Character.isISOControl(it)) ' ' else it }?.joinToString("")?.trim()
        if(cleaned.isNullOrEmpty() || cleaned.equals("unknown",ignoreCase=true)) return null
        return if(cleaned.length>512) cleaned.take(512)+"…" else cleaned
    }
    fun bytes(value: Long?): String? {
        if(value==null || value<0) return null
        val units=listOf("B","KiB","MiB","GiB","TiB","PiB","EiB")
        var amount=value.toDouble();var unit=0
        while(amount>=1024 && unit<units.lastIndex) { amount/=1024;unit++ }
        return if(unit==0) "$value B" else String.format(Locale.ROOT,"%.2f %s",amount,units[unit])
    }
    fun uptime(value: Long): String? {
        if(value<0) return null
        val seconds=value/1000
        return String.format(Locale.ROOT,"%d d %02d:%02d:%02d",seconds/86400,seconds/3600%24,seconds/60%60,seconds%60)
    }
}
