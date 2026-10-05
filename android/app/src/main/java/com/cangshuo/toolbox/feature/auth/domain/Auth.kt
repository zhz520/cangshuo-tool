package com.cangshuo.toolbox.feature.auth.domain

import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

data class AccountProfile(val id: Long, val email: String, val nickname: String)

enum class AuthFailure { INVALID_EMAIL, INVALID_PASSWORD, INVALID_NICKNAME, PASSWORD_MISMATCH,
    CREDENTIALS, ACCOUNT_EXISTS, EXPIRED, NETWORK, SERVICE, STORAGE }

enum class AuthStatus { SIGNED_OUT, RESTORING, SIGNED_IN, RETRY_REQUIRED }

class AuthException(val failure: AuthFailure) : Exception(failure.name)

interface AuthRepository {
    val account: StateFlow<AccountProfile?>
    val status: StateFlow<AuthStatus>
    suspend fun register(email: String, password: String, nickname: String)
    suspend fun login(email: String, password: String)
    suspend fun logout()
    suspend fun restore()
    suspend fun authorization(): String
    suspend fun updateProfile(nickname: String)
}

object AuthInput {
    private val emailPattern = Regex("^[A-Za-z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#\$%&'*+/=?^_`{|}~-]+)*@" +
        "[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+\$")

    fun email(value: String): String = value.trim().lowercase(Locale.ROOT).also {
        if (it.length > 128 || !emailPattern.matches(it) || it.indexOf('@') > 64) fail(AuthFailure.INVALID_EMAIL)
    }

    fun password(value: String) {
        if (value.length !in 8..72 || value.toByteArray(Charsets.UTF_8).size > 72 ||
            value.any { Character.isISOControl(it) } || !validUnicode(value)) fail(AuthFailure.INVALID_PASSWORD)
    }

    fun nickname(value: String): String = value.trim().also {
        if (it.isEmpty() || it.length > 64 || it.any { c -> Character.isISOControl(c) } || !validUnicode(it))
            fail(AuthFailure.INVALID_NICKNAME)
    }

    private fun validUnicode(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (Character.isHighSurrogate(char)) {
                if (index == value.length || !Character.isLowSurrogate(value[index++])) return false
            } else if (Character.isLowSurrogate(char)) return false
        }
        return true
    }

    private fun fail(failure: AuthFailure): Nothing = throw AuthException(failure)
}

class AuthUseCases(private val repository: AuthRepository) {
    val account get() = repository.account
    val status get() = repository.status
    suspend fun login(email: String, password: String) {
        AuthInput.password(password)
        repository.login(AuthInput.email(email), password)
    }
    suspend fun register(email: String, password: String, confirmation: String, nickname: String) {
        AuthInput.password(password)
        if (password != confirmation) throw AuthException(AuthFailure.PASSWORD_MISMATCH)
        repository.register(AuthInput.email(email), password, AuthInput.nickname(nickname))
    }
    suspend fun logout() = repository.logout()
    suspend fun restore() = repository.restore()
    suspend fun updateProfile(nickname: String) = repository.updateProfile(AuthInput.nickname(nickname))
}
