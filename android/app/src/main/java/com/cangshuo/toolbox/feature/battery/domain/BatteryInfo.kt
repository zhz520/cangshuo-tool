package com.cangshuo.toolbox.feature.battery.domain

import kotlinx.coroutines.flow.Flow

data class BatterySnapshot(val available: Boolean, val present: Boolean?, val percent: Double?,
    val status: Int?, val health: Int?, val plugged: Int?, val temperatureC: Double?, val voltageMv: Int?,
    val technology: String?, val currentUa: Int?, val chargeUah: Int?, val powerSave: Boolean?) {
    override fun toString() = "BatterySnapshot[redacted]"
}
object BatteryPolicy {
    fun percent(level: Int?, scale: Int?): Double? = if (level != null && scale != null && scale > 0 && level in 0..scale)
        level.toDouble() / scale * 100 else null
    fun temperature(tenths: Int?): Double? = tenths?.takeIf { it in -1000..2000 }?.div(10.0)
    fun property(value: Int?): Int? = value?.takeUnless { it == Int.MIN_VALUE }
}
interface BatteryInfoRepository { fun observe(): Flow<BatterySnapshot> }
class ObserveBatteryInfoUseCase(private val repository: BatteryInfoRepository) {
    operator fun invoke() = repository.observe()
}
