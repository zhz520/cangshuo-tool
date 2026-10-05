package com.cangshuo.toolbox.feature.auth.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuthInputTest {
    @Test fun emailAndNicknameNormalization() {
        assertEquals("user+tag@example.com", AuthInput.email(" USER+tag@Example.com "))
        assertEquals("用户", AuthInput.nickname(" 用户 "))
    }
    @Test fun invalidEmailsAreRejected() {
        listOf("", "x", "a@localhost", "a..b@example.com", "a@-example.com", "你@example.com").forEach { value ->
            assertEquals(AuthFailure.INVALID_EMAIL, failure { AuthInput.email(value) })
        }
    }
    @Test fun passwordUtf8LimitAndUnicodeAreStrict() {
        AuthInput.password("密".repeat(24))
        listOf("密".repeat(25), "a".repeat(73), "short", "secret12\u0000", "secret12\uD800").forEach { value ->
            assertEquals(AuthFailure.INVALID_PASSWORD, failure { AuthInput.password(value) })
        }
    }
    @Test fun nicknameControlsAndUnpairedSurrogatesAreRejected() {
        listOf("", " ", "a".repeat(65), "name\nother", "\uDC00").forEach { value ->
            assertEquals(AuthFailure.INVALID_NICKNAME, failure { AuthInput.nickname(value) })
        }
    }
    @Test fun confirmationPreventsRemoteRegistration() = runTest {
        val repository = FakeRepository()
        try { AuthUseCases(repository).register("a@b.com", "password12", "different", "u"); fail() }
        catch (exception: AuthException) { assertEquals(AuthFailure.PASSWORD_MISMATCH, exception.failure) }
        assertEquals(0, repository.calls)
    }
    @Test fun useCaseNormalizesEmailAndPreservesPassword() = runTest {
        val repository = FakeRepository()
        AuthUseCases(repository).login(" A@B.COM ", "  secret  ")
        assertEquals("a@b.com", repository.email)
        assertEquals("  secret  ", repository.password)
    }
    private fun failure(action: () -> Unit): AuthFailure {
        try { action(); fail("Expected validation error") } catch (exception: AuthException) { return exception.failure }
        throw AssertionError()
    }
    private class FakeRepository : AuthRepository {
        override val account = MutableStateFlow<AccountProfile?>(null)
        override val status = MutableStateFlow(AuthStatus.SIGNED_OUT)
        var calls = 0; var email = ""; var password = ""
        override suspend fun register(email: String, password: String, nickname: String) { calls++ }
        override suspend fun login(email: String, password: String) { calls++; this.email = email; this.password = password }
        override suspend fun logout() { account.value = null }
        override suspend fun updateProfile(nickname: String) {}
        override suspend fun restore() {}
        override suspend fun authorization() = "Bearer test"
    }
}
