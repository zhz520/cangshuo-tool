package com.cangshuo.toolbox.feature.level.domain

import kotlinx.coroutines.flow.Flow
import kotlin.math.*

data class LevelAngles(val x: Double, val y: Double, val tilt: Double, val faceUp: Boolean)
data class LevelReading(val angles: LevelAngles, val rotation: Int, val accuracy: Int, val accelerationFallback: Boolean)
class LevelUnavailable : IllegalStateException("Level unavailable")
interface LevelRepository { fun observe(): Flow<LevelReading?> }
class ObserveLevelUseCase(private val repository: LevelRepository) { operator fun invoke() = repository.observe() }
object LevelMath {
    fun fromGravity(x: Double, y: Double, z: Double, rotation: Int): LevelAngles? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
        val magnitude = hypot(hypot(x,y),z)
        if (magnitude !in 5.0..15.0) return null
        val (sx,sy) = when(rotation) { 1 -> y to -x; 2 -> -x to -y; 3 -> -y to x; else -> x to y }
        return LevelAngles(Math.toDegrees(atan2(sx,hypot(sy,z))), Math.toDegrees(atan2(sy,hypot(sx,z))),
            Math.toDegrees(acos((z/magnitude).coerceIn(-1.0,1.0))), z > 0)
    }
    fun relative(current: LevelAngles, reference: LevelAngles?) = current.copy(
        x = current.x - (reference?.x ?: 0.0), y = current.y - (reference?.y ?: 0.0))
    fun nearZero(angles: LevelAngles): Boolean = angles.faceUp && abs(angles.x) <= .5 && abs(angles.y) <= .5
}
