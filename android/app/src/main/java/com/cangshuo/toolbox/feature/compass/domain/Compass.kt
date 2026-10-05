package com.cangshuo.toolbox.feature.compass.domain

import kotlinx.coroutines.flow.Flow

data class CompassReading(val heading: Double?, val accuracy: Int, val angularError: Double?,
    val needsFlat: Boolean, val rotationVector: Boolean)
class CompassUnavailable : IllegalStateException("Compass unavailable")
interface CompassRepository { fun observe(): Flow<CompassReading> }
class ObserveCompassUseCase(private val repository: CompassRepository) { operator fun invoke() = repository.observe() }
object CompassAngles {
    fun normalize(degrees: Double): Double? = degrees.takeIf(Double::isFinite)?.let { ((it % 360) + 360) % 360 }
    fun sector(degrees: Double): Int? = normalize(degrees)?.let { ((it + 22.5) / 45).toInt() % 8 }
}
