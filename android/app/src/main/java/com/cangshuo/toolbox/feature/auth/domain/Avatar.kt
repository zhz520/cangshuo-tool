package com.cangshuo.toolbox.feature.auth.domain

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

object QqAvatarPolicy {
    private val numberPattern = Regex("[1-9][0-9]{4,11}")
    fun valid(number: String): Boolean = numberPattern.matches(number)
    fun fromEmail(email: String): String? {
        val normalized = email.trim()
        return normalized.substringBefore('@').takeIf {
            normalized.substringAfter('@').equals("qq.com", ignoreCase = true) && valid(it)
        }
    }
    fun url(number: String): String {
        require(valid(number))
        return "https://q.qlogo.cn/headimg_dl?dst_uin=$number&spec=640&img_type=jpg"
    }
}

interface AvatarRepository {
    fun observeQq(userId: Long): Flow<String?>
    suspend fun saveQq(userId: Long, number: String)
    suspend fun load(number: String): Bitmap
}

class AvatarUseCases(private val repository: AvatarRepository) {
    fun observe(userId: Long) = repository.observeQq(userId)
    suspend fun save(userId: Long, number: String) {
        val normalized = number.trim()
        require(userId > 0 && (normalized.isEmpty() || QqAvatarPolicy.valid(normalized)))
        repository.saveQq(userId, normalized)
    }
    suspend fun load(number: String) = repository.load(number)
}
