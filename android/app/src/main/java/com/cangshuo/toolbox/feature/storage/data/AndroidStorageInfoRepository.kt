package com.cangshuo.toolbox.feature.storage.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.cangshuo.toolbox.feature.deviceinfo.domain.DeviceInfoFormat
import com.cangshuo.toolbox.feature.storage.domain.*
import java.io.File
import kotlinx.coroutines.*

class AndroidStorageInfoRepository(context: Context) : StorageInfoRepository {
    private val context = context.applicationContext
    override suspend fun read(): StorageSnapshot = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(StorageManager::class.java)
        val internalPath = context.filesDir
        val volumes = mutableListOf(StorageVolumeInfo(null, true, false, VolumeState.MOUNTED, capacity(internalPath)))
        var partial = volumes.first().capacity == null
        val seen = mutableSetOf<String>()
        runCatching { manager?.getUuidForPath(internalPath)?.toString() }.getOrNull()?.let(seen::add)
        val descriptors = runCatching { manager?.storageVolumes }.getOrNull()
        if (descriptors == null) return@withContext StorageSnapshot(volumes, true)
        // API 26–29 has no public volume directory accessor. Only app-owned external directories are queried.
        val appDirectories = if (Build.VERSION.SDK_INT < 30) runCatching { context.getExternalFilesDirs(null).filterNotNull() }.getOrDefault(emptyList()) else emptyList()
        if (descriptors.size > 16) partial = true
        for (volume in descriptors.take(16)) {
            ensureActive()
            // Primary emulated storage uses the internal data filesystem; do not add its capacity twice.
            if (volume.isPrimary && volume.isEmulated) continue
            val path = if (Build.VERSION.SDK_INT >= 30) volume.directory else appDirectories.firstOrNull {
                runCatching { manager?.getStorageVolume(it) == volume }.getOrDefault(false)
            }
            val key = path?.let { runCatching { manager?.getUuidForPath(it)?.toString() }.getOrNull() } ?: volume.uuid
            if (key != null && !seen.add(key)) continue
            val state = when (volume.state) {
                Environment.MEDIA_MOUNTED -> VolumeState.MOUNTED
                Environment.MEDIA_MOUNTED_READ_ONLY -> VolumeState.READ_ONLY
                else -> VolumeState.UNAVAILABLE
            }
            val capacity = if (state != VolumeState.UNAVAILABLE && path != null) capacity(path) else null
            if (state != VolumeState.UNAVAILABLE && capacity == null) partial = true
            volumes += StorageVolumeInfo(DeviceInfoFormat.text(runCatching { volume.getDescription(context) }.getOrNull()),
                false, volume.isRemovable, state, capacity)
        }
        StorageSnapshot(volumes.toList(), partial)
    }
    private fun capacity(path: File): StorageCapacity? = runCatching {
        val stats = StatFs(path.absolutePath)
        StorageCapacity.create(stats.totalBytes, stats.freeBytes, stats.availableBytes)
    }.getOrNull()
}
