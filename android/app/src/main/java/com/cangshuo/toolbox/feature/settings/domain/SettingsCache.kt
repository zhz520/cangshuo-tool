package com.cangshuo.toolbox.feature.settings.domain

interface SettingsCacheRepository {
    suspend fun sizeBytes(): Long
    suspend fun clear()
}

class SettingsCacheUseCases(private val repository: SettingsCacheRepository) {
    suspend fun sizeBytes() = repository.sizeBytes()
    suspend fun clear() = repository.clear()
}
