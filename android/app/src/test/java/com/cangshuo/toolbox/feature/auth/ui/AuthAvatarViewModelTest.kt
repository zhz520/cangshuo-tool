package com.cangshuo.toolbox.feature.auth.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.auth.domain.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthAvatarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    private fun model(auth: Accounts, avatars: Avatars) = AuthViewModel(AuthUseCases(auth), AvatarUseCases(avatars))
        .also { models.put("auth", it) }

    @Test fun qqEmailIsDetectedAndClearingReallyResetsTheAvatar() = runTest {
        val auth = Accounts(); val avatars = Avatars(); val model = model(auth, avatars)
        runCurrent()
        assertEquals("14329140", model.uiState.value.savedQqNumber)
        assertEquals(listOf("14329140"), avatars.loads)
        model.qqNumber(""); model.saveAvatar(); runCurrent()
        assertEquals("", avatars.preferences.getValue(1L).value)
        assertEquals("", model.uiState.value.savedQqNumber)
        assertTrue(model.uiState.value.avatarSaved)
        assertFalse(model.uiState.value.avatarLoading)
        assertFalse(model.uiState.value.avatarFailed)
        assertEquals(1, avatars.loads.size)
    }

    @Test fun invalidAndOverlongInputCannotRequestOrSaveAnAvatar() = runTest {
        val auth = Accounts("person@example.com"); val avatars = Avatars(); val model = model(auth, avatars)
        runCurrent()
        listOf("1234", "14329140&x=1", "1234567890123").forEach { number ->
            model.qqNumber(number); model.saveAvatar(); runCurrent()
            assertTrue(model.uiState.value.qqInvalid)
        }
        assertEquals(0, avatars.saves)
        assertTrue(avatars.loads.isEmpty())
    }

    @Test fun storageFailureKeepsInputAndDoesNotReportAnImageLoadFailure() = runTest {
        val auth = Accounts("person@example.com"); val avatars = Avatars(); val model = model(auth, avatars)
        runCurrent(); avatars.failSave = true
        model.qqNumber("14329140"); model.saveAvatar(); runCurrent()
        assertTrue(model.uiState.value.avatarSaveFailed)
        assertFalse(model.uiState.value.avatarFailed)
        assertFalse(model.uiState.value.avatarSaved)
        assertFalse(model.uiState.value.avatarSaving)
        assertEquals("14329140", model.uiState.value.qqNumber)
        avatars.failSave = false; model.saveAvatar(); runCurrent()
        assertFalse(model.uiState.value.avatarSaveFailed)
        assertTrue(model.uiState.value.avatarSaved)
    }

    @Test fun delayedPreferenceDoesNotOverwriteAnUnsavedQqEdit() = runTest {
        val auth = Accounts("person@example.com"); val avatars = Avatars(); val model = model(auth, avatars)
        runCurrent(); model.qqNumber("14329140")
        avatars.preferences.getValue(1L).value = "543210"
        runCurrent()
        assertEquals("14329140", model.uiState.value.qqNumber)
        assertEquals("543210", model.uiState.value.savedQqNumber)
    }

    @Test fun switchingAccountsCancelsSaveAndResetsBusyState() = runTest {
        val auth = Accounts("person@example.com"); val avatars = Avatars(); val model = model(auth, avatars)
        runCurrent(); avatars.saveGate = CompletableDeferred()
        model.qqNumber("14329140"); model.saveAvatar(); runCurrent()
        assertTrue(model.uiState.value.avatarSaving)
        auth.account.value = AccountProfile(2L, "other@example.com", "Other"); runCurrent()
        assertFalse(model.uiState.value.avatarSaving)
        assertEquals("", model.uiState.value.qqNumber)
        assertFalse(model.uiState.value.avatarSaved)
        avatars.saveGate!!.complete(Unit); runCurrent()
        assertNull(avatars.preferences.getValue(1L).value)
        assertFalse(model.uiState.value.avatarSaved)
    }

    @Test fun failureFromACancelledRequestCannotReplaceNewerAvatarState() = runTest {
        val auth = Accounts(); val avatars = Avatars(); val gate = CompletableDeferred<Unit>()
        avatars.loadGate = gate
        val model = model(auth, avatars); runCurrent()
        assertTrue(model.uiState.value.avatarLoading)
        model.qqNumber(""); model.saveAvatar(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertFalse(model.uiState.value.avatarFailed)
        assertFalse(model.uiState.value.avatarLoading)
        assertEquals("", model.uiState.value.savedQqNumber)
    }

    private class Accounts(email: String = "14329140@qq.com") : AuthRepository {
        override val account = MutableStateFlow<AccountProfile?>(AccountProfile(1L, email, "User"))
        override val status = MutableStateFlow(AuthStatus.SIGNED_IN)
        override suspend fun register(email: String, password: String, nickname: String) = Unit
        override suspend fun login(email: String, password: String) = Unit
        override suspend fun logout() { account.value = null }
        override suspend fun restore() = Unit
        override suspend fun authorization() = "Bearer test"
        override suspend fun updateProfile(nickname: String) = Unit
        override suspend fun deleteAccount(user: Long, email: String, password: String) { account.value = null }
    }

    private class Avatars : AvatarRepository {
        val preferences = mutableMapOf<Long, MutableStateFlow<String?>>()
        val loads = mutableListOf<String>()
        var saves = 0
        var failSave = false
        var saveGate: CompletableDeferred<Unit>? = null
        var loadGate: CompletableDeferred<Unit>? = null
        override fun observeQq(userId: Long): Flow<String?> = preferences.getOrPut(userId) { MutableStateFlow(null) }
        override suspend fun saveQq(userId: Long, number: String) {
            saves++
            saveGate?.await()
            if (failSave) throw IOException("Preference unavailable")
            preferences.getValue(userId).value = number
        }
        override suspend fun load(number: String): Bitmap {
            loads += number
            // Simulate a provider completing an old request after cancellation.
            loadGate?.let { withContext(NonCancellable) { it.await(); throw IOException("Old image unavailable") } }
            throw IOException("Image unavailable")
        }
    }
}
