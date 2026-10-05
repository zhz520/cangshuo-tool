package com.cangshuo.toolbox.feature.auth.data

import android.content.Context
import com.cangshuo.toolbox.feature.auth.domain.*
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import retrofit2.Response

class RemoteAuthRepository internal constructor(private val api: AuthApi, private val scope: CoroutineScope,
    private val store: AuthSessionStore = MemoryAuthSessionStore()) : AuthRepository {
    constructor(baseUrl: String, scope: CoroutineScope) : this(createAuthApi(baseUrl), scope)
    constructor(baseUrl: String, scope: CoroutineScope, context: Context) :
        this(createAuthApi(baseUrl), scope, EncryptedAuthSessionStore(context, baseUrl, scope))
    private val mutableAccount = MutableStateFlow<AccountProfile?>(null)
    override val account = mutableAccount.asStateFlow()
    private val mutableStatus = MutableStateFlow(AuthStatus.SIGNED_OUT)
    override val status = mutableStatus.asStateFlow()
    private val lock = Mutex()
    private var session = StoredAuthSession()
    private var token: String? = null
    private var accessDeadline = 0L
    private var renew: Job? = null
    private var loaded = false

    override suspend fun register(email: String, password: String, nickname: String) = authenticate {
        api.register(AuthRequestDto(AuthInput.email(email), password.also(AuthInput::password), AuthInput.nickname(nickname)))
    }
    override suspend fun login(email: String, password: String) = authenticate {
        api.login(AuthRequestDto(AuthInput.email(email), password.also(AuthInput::password)))
    }
    private suspend fun authenticate(request: suspend () -> Response<AuthEnvelope<AuthDataDto>>) = lock.withLock {
        protect { load(); flushLogout(); accept(successful(request())) }
    }

    override suspend fun restore() = lock.withLock {
        mutableStatus.value = AuthStatus.RESTORING
        try {
            protect {
                load()
                if (session.pendingLogout) { flushLogout(); mutableStatus.value = AuthStatus.SIGNED_OUT; return@protect }
                if (session.refreshToken.isEmpty()) { mutableStatus.value = AuthStatus.SIGNED_OUT; return@protect }
                if (!refreshValid()) { clear(); return@protect }
                refresh()
            }
        } catch (exception: AuthException) {
            if (exception.failure == AuthFailure.EXPIRED) clear() else mutableStatus.value = AuthStatus.RETRY_REQUIRED
            throw exception
        }
    }

    override suspend fun authorization(): String = lock.withLock {
        protect { bearer() }
    }

    private suspend fun bearer(): String {
            load()
            if (session.pendingLogout) fail(AuthFailure.EXPIRED)
            if (!refreshValid()) { clear(); fail(AuthFailure.EXPIRED) }
            if (token == null || accessDeadline <= System.currentTimeMillis() + 30_000) refresh()
            return "Bearer ${token ?: fail(AuthFailure.EXPIRED)}"
    }

    override suspend fun updateProfile(nickname: String) = lock.withLock {
        protect {
            val value = AuthInput.nickname(nickname)
            var response = api.updateProfile(bearer(), ProfileRequestDto(value))
            if (response.code() == 401) {
                response.errorBody()?.close()
                refresh()
                response = api.updateProfile(bearer(), ProfileRequestDto(value))
                if (response.code() == 401) { response.errorBody()?.close(); clear(); fail(AuthFailure.EXPIRED) }
            }
            val updated = successful(response)
            if (updated.id != mutableAccount.value?.id || updated.nickname != value) fail(AuthFailure.SERVICE)
            mutableAccount.value = AccountProfile(updated.id, updated.email, updated.nickname)
        }
    }

    override suspend fun logout() = lock.withLock {
        load()
        renew?.cancel(); renew = null
        token = null; accessDeadline = 0; mutableAccount.value = null; mutableStatus.value = AuthStatus.SIGNED_OUT
        if (session.refreshToken.isEmpty()) return@withLock
        session = session.copy(pendingLogout = true)
        persist(session)
        try { protect { flushLogout() } }
        catch (exception: AuthException) {
            if (exception.failure != AuthFailure.NETWORK && exception.failure != AuthFailure.SERVICE) throw exception
        }
    }

    private suspend fun load() {
        if (!loaded) {
            try { session = store.read(); loaded = true }
            catch (exception: CancellationException) { throw exception }
            catch (_: Exception) { fail(AuthFailure.STORAGE) }
        }
    }
    private fun refreshValid(): Boolean = session.refreshToken.matches(Regex("[0-9a-f]{32}\\.[A-Za-z0-9_-]{43}")) &&
        try { Instant.parse(session.refreshExpiresAt).toEpochMilli() > System.currentTimeMillis() } catch (_: Exception) { false }

    private suspend fun refresh() {
        val response = api.refresh(RefreshRequestDto(session.refreshToken))
        if (response.code() == 401) { response.errorBody()?.close(); clear(); fail(AuthFailure.EXPIRED) }
        accept(successful(response))
    }

    private suspend fun accept(data: AuthDataDto) {
        val deadline = try { Instant.parse(data.expiresAt).toEpochMilli() } catch (_: Exception) { fail(AuthFailure.SERVICE) }
        val refreshDeadline = try { Instant.parse(data.refreshExpiresAt).toEpochMilli() } catch (_: Exception) { fail(AuthFailure.SERVICE) }
        val now = System.currentTimeMillis()
        if (data.tokenType != "Bearer" || data.accessToken.length !in 32..4096 || data.expiresIn !in 1..900 ||
            deadline <= now || deadline > now + 960_000 || data.user.id <= 0 || refreshDeadline <= now ||
            refreshDeadline > now + 2_592_060_000 || !data.refreshToken.matches(Regex("[0-9a-f]{32}\\.[A-Za-z0-9_-]{43}")))
            fail(AuthFailure.SERVICE)
        val next = StoredAuthSession(data.refreshToken, data.refreshExpiresAt)
        // Commit a received replacement before a secondary request can be cancelled.
        withContext(NonCancellable) { persist(next); session = next }
        // Keep the still-valid identity during a same-account renewal, so its sync job
        // is not cancelled by a transient signed-out state while /me is in flight.
        if (mutableAccount.value?.id != data.user.id) {
            token = null; accessDeadline = 0; mutableAccount.value = null
        }
        val profileResponse = api.me("Bearer ${data.accessToken}")
        if (profileResponse.code() == 401) { profileResponse.errorBody()?.close(); clear(); fail(AuthFailure.EXPIRED) }
        val current = successful(profileResponse)
        if (current.id != data.user.id) fail(AuthFailure.SERVICE)
        coroutineContext.ensureActive()
        token = data.accessToken; accessDeadline = deadline
        mutableAccount.value = AccountProfile(current.id, current.email, current.nickname)
        mutableStatus.value = AuthStatus.SIGNED_IN
        scheduleRenewal()
    }

    private fun scheduleRenewal() {
        renew?.cancel()
        val expectedToken = token
        val expectedDeadline = accessDeadline
        renew = scope.launch {
            delay((expectedDeadline - System.currentTimeMillis() - 30_000).coerceAtLeast(1000))
            var expireLater = false
            lock.withLock {
                if (token != expectedToken) return@withLock
                try { protect { refresh() } }
                catch (exception: AuthException) {
                    if (exception.failure == AuthFailure.EXPIRED) clear() else expireLater = true
                }
            }
            if (expireLater) {
                delay((expectedDeadline - System.currentTimeMillis()).coerceAtLeast(0))
                lock.withLock {
                    if (token == expectedToken) {
                        token = null; mutableAccount.value = null; mutableStatus.value = AuthStatus.RETRY_REQUIRED
                    }
                }
            }
        }
    }
    private suspend fun flushLogout() {
        if (!session.pendingLogout) return
        if (!session.refreshToken.matches(Regex("[0-9a-f]{32}\\.[A-Za-z0-9_-]{43}"))) { clear(); return }
        val response = api.logout(RefreshRequestDto(session.refreshToken))
        response.errorBody()?.close()
        if (!response.isSuccessful && response.code() != 401) fail(AuthFailure.SERVICE)
        clear()
    }
    private suspend fun clear() {
        persist(StoredAuthSession())
        session = StoredAuthSession(); token = null; accessDeadline = 0; mutableAccount.value = null
        mutableStatus.value = AuthStatus.SIGNED_OUT
    }
    private suspend fun persist(value: StoredAuthSession) {
        try { store.write(value) } catch (exception: CancellationException) { throw exception }
        catch (_: Exception) { fail(AuthFailure.STORAGE) }
    }
    private fun <T> successful(response: Response<AuthEnvelope<T>>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            fail(when (response.code()) { 401 -> AuthFailure.CREDENTIALS; 409 -> AuthFailure.ACCOUNT_EXISTS; else -> AuthFailure.SERVICE })
        }
        val envelope = response.body() ?: fail(AuthFailure.SERVICE)
        if (envelope.code != 0) fail(AuthFailure.SERVICE)
        return envelope.data ?: fail(AuthFailure.SERVICE)
    }
    private suspend fun <T> protect(operation: suspend () -> T): T = try { operation() }
        catch (exception: CancellationException) { throw exception }
        catch (exception: AuthException) { throw exception }
        catch (_: IOException) { fail(AuthFailure.NETWORK) }
        catch (_: Exception) { fail(AuthFailure.SERVICE) }
    private fun fail(failure: AuthFailure): Nothing = throw AuthException(failure)
}
