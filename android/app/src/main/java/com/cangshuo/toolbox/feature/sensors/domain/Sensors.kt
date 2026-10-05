package com.cangshuo.toolbox.feature.sensors.domain

import kotlinx.coroutines.flow.Flow

data class SensorDescriptor(val id: Int, val name: String, val vendor: String, val type: Int,
    val version: Int, val range: Float, val resolution: Float, val powerMa: Float,
    val minimumDelayUs: Int, val wakeUp: Boolean, val liveSupported: Boolean)
data class SensorCatalog(val sensors: List<SensorDescriptor>, val truncated: Boolean)
data class SensorReading(val values: List<Float?>, val accuracy: Int, val elapsedNanos: Long, val truncated: Boolean)
interface SensorsRepository {
    suspend fun catalog(): SensorCatalog
    fun observe(id: Int): Flow<SensorReading>
}
class SensorsUseCases(private val repository: SensorsRepository) {
    suspend fun catalog() = repository.catalog()
    fun observe(id: Int) = repository.observe(id)
}
object SensorUnits {
    fun unit(type: Int) = when(type) {
        1, 9, 10 -> "m/s²"
        2, 14 -> "µT"
        4, 16 -> "rad/s"
        5 -> "lx"
        6 -> "hPa"
        8 -> "cm"
        12 -> "%"
        13 -> "°C"
        else -> ""
    }
    val liveTypes = setOf(1,2,4,5,6,8,9,10,11,12,13,14,15,16,20)
}
