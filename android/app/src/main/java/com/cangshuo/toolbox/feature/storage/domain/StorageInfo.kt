package com.cangshuo.toolbox.feature.storage.domain

class StorageCapacity private constructor(val total: Long, val free: Long, val available: Long) {
    val used: Long get() = total - free
    val reserved: Long get() = free - available
    val usedFraction: Float get() = (used.toDouble() / total).toFloat().coerceIn(0f, 1f)
    companion object {
        fun create(total: Long, free: Long, available: Long): StorageCapacity? =
            if (total > 0 && free in 0..total && available in 0..free) StorageCapacity(total, free, available) else null
    }
}
enum class VolumeState { MOUNTED, READ_ONLY, UNAVAILABLE }
data class StorageVolumeInfo(val name: String?, val internal: Boolean, val removable: Boolean,
    val state: VolumeState, val capacity: StorageCapacity?)
data class StorageSnapshot(val volumes: List<StorageVolumeInfo>, val partial: Boolean) {
    override fun toString() = "StorageSnapshot[redacted]"
}
interface StorageInfoRepository { suspend fun read(): StorageSnapshot }
class ReadStorageInfoUseCase(private val repository: StorageInfoRepository) {
    suspend operator fun invoke() = repository.read()
}
