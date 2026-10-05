package com.cangshuo.toolbox.feature.auth.data

import com.cangshuo.toolbox.feature.auth.domain.*
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class RefreshSessionRepositoryTest {
    private fun refresh(index: Int) = "12".repeat(16) + "." + index.toString().repeat(43)
    private fun data(index: Int) = AuthDataDto("token".repeat(10), "Bearer", 900, Instant.now().plusSeconds(900).toString(),
        UserDto(1,"a@b.com","u"), refresh(index), Instant.now().plusSeconds(2592000).toString())
    private fun <T> success(value: T) = Response.success(AuthEnvelope(0,"success",value,"trace"))

    @Test fun processRestartRefreshesStoredTokenBeforePublishing() = runTest {
        val store = MemoryAuthSessionStore()
        val api = FakeApi()
        val first = RemoteAuthRepository(api, backgroundScope, store)
        first.login("a@b.com","password12")
        assertEquals(refresh(1),store.read().refreshToken)
        val restarted = RemoteAuthRepository(api, backgroundScope, store)
        assertNull(restarted.account.value)
        restarted.restore()
        assertEquals(refresh(1),api.lastRefresh)
        assertEquals(refresh(2),store.read().refreshToken)
        assertEquals(AuthStatus.SIGNED_IN,restarted.status.value)
        assertEquals(1L,restarted.account.value?.id)
    }
    @Test fun refreshUnauthorizedClearsPersistentState() = runTest {
        val store = MemoryAuthSessionStore()
        store.write(StoredAuthSession(refresh(1),Instant.now().plusSeconds(300).toString()))
        val api = FakeApi().apply { rejected = true }
        val repo = RemoteAuthRepository(api,backgroundScope,store)
        try { repo.restore(); fail() } catch (exception: AuthException) { assertEquals(AuthFailure.EXPIRED,exception.failure) }
        assertEquals("",store.read().refreshToken)
        assertNull(repo.account.value)
    }
    @Test fun offlineRestorePreservesTokenAndOffersRetry() = runTest {
        val store = MemoryAuthSessionStore()
        store.write(StoredAuthSession(refresh(1),Instant.now().plusSeconds(300).toString()))
        val api = FakeApi().apply { offline = true }
        val repo = RemoteAuthRepository(api,backgroundScope,store)
        try { repo.restore(); fail() } catch (exception: AuthException) { assertEquals(AuthFailure.NETWORK,exception.failure) }
        assertEquals(refresh(1),store.read().refreshToken)
        assertEquals(AuthStatus.RETRY_REQUIRED,repo.status.value)
        assertNull(repo.account.value)
    }
    @Test fun offlineLogoutPersistsTombstoneAndRestartOnlyRevokes() = runTest {
        val store = MemoryAuthSessionStore(); val api = FakeApi()
        val repo = RemoteAuthRepository(api,backgroundScope,store)
        repo.login("a@b.com","password12")
        api.offline = true
        repo.logout()
        assertNull(repo.account.value)
        assertTrue(store.read().pendingLogout)
        api.offline = false
        RemoteAuthRepository(api,backgroundScope,store).restore()
        assertEquals(refresh(1),api.lastLogout)
        assertNull(api.lastRefresh)
        assertEquals("",store.read().refreshToken)
    }
    @Test fun expiredStoredSessionIsClearedWithoutNetwork() = runTest {
        val store = MemoryAuthSessionStore(); val api = FakeApi()
        store.write(StoredAuthSession(refresh(1),"2020-01-01T00:00:00Z"))
        val repo = RemoteAuthRepository(api,backgroundScope,store)
        repo.restore()
        assertNull(api.lastRefresh)
        assertEquals("",store.read().refreshToken)
    }
    @Test fun receivedRotationIsSavedBeforeMeFailure() = runTest {
        val store = MemoryAuthSessionStore(); val api = FakeApi().apply { meFailure = true }
        store.write(StoredAuthSession(refresh(1),Instant.now().plusSeconds(300).toString()))
        val repo = RemoteAuthRepository(api,backgroundScope,store)
        try { repo.restore(); fail() } catch (_: AuthException) { }
        assertEquals(refresh(2),store.read().refreshToken)
        assertNull(repo.account.value)
    }
    @Test fun profileEditPublishesOnlyConfirmedServerResponse() = runTest {
        val repo = RemoteAuthRepository(FakeApi(),backgroundScope)
        repo.login("a@b.com","password12")
        repo.updateProfile(" 新昵称 ")
        assertEquals("新昵称",repo.account.value?.nickname)
        try { repo.updateProfile("bad\nname"); fail() } catch (e: AuthException) { assertEquals(AuthFailure.INVALID_NICKNAME,e.failure) }
        assertEquals("新昵称",repo.account.value?.nickname)
    }
    @Test fun sameAccountRenewalKeepsIdentityWhileMeIsInFlight() = runTest {
        val api=FakeApi();val repo=RemoteAuthRepository(api,backgroundScope)
        repo.login("a@b.com","password12")
        api.onMe={assertEquals(1L,repo.account.value?.id)}
        repo.restore()
        assertEquals(AuthStatus.SIGNED_IN,repo.status.value)
    }
    @Test fun disabledMeClearsTheRenewingAccountImmediately() = runTest {
        val api=FakeApi();val repo=RemoteAuthRepository(api,backgroundScope)
        repo.login("a@b.com","password12");api.meRejected=true
        try {repo.restore();fail()} catch(e: AuthException) {assertEquals(AuthFailure.EXPIRED,e.failure)}
        assertNull(repo.account.value)
    }
    private inner class FakeApi : AuthApi {
        override suspend fun updateProfile(bearer: String, request: ProfileRequestDto) = success(UserDto(1,"a@b.com",request.nickname))
        var offline=false; var rejected=false; var meFailure=false
        var meRejected=false;var onMe: (() -> Unit)?=null
        var lastRefresh: String?=null; var lastLogout: String?=null
        override suspend fun login(request: AuthRequestDto) = success(data(1))
        override suspend fun register(request: AuthRequestDto) = success(data(1))
        override suspend fun me(bearer: String): Response<AuthEnvelope<UserDto>> {
            onMe?.invoke()
            if(meRejected) return Response.error(401,ResponseBody.create(MediaType.parse("application/json"),"{}"))
            if (meFailure) throw IOException("offline")
            return success(UserDto(1,"a@b.com","u"))
        }
        override suspend fun refresh(request: RefreshRequestDto): Response<AuthEnvelope<AuthDataDto>> {
            if (offline) throw IOException("offline")
            lastRefresh=request.refreshToken
            if (rejected) return Response.error(401,ResponseBody.create(MediaType.parse("application/json"),"{}"))
            return success(data(2))
        }
        override suspend fun logout(request: RefreshRequestDto): Response<AuthEnvelope<Unit>> {
            if (offline) throw IOException("offline")
            lastLogout=request.refreshToken
            return success(Unit)
        }
    }
}
