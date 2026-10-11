package com.cangshuo.toolbox.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.auth.domain.AccountProfile
import com.cangshuo.toolbox.feature.auth.domain.AuthException
import com.cangshuo.toolbox.feature.auth.domain.AuthFailure
import com.cangshuo.toolbox.feature.auth.domain.AuthUseCases
import com.cangshuo.toolbox.feature.auth.domain.AuthStatus
import com.cangshuo.toolbox.feature.auth.domain.AvatarUseCases
import com.cangshuo.toolbox.feature.auth.domain.QqAvatarPolicy
import android.graphics.Bitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(val account: AccountProfile? = null, val registering: Boolean = false,
    val email: String = "", val nickname: String = "", val password: String = "", val confirmation: String = "",
    val busy: Boolean = false, val failure: AuthFailure? = null, val failureDetail: String? = null,
    val status: AuthStatus = AuthStatus.SIGNED_OUT,
    val profileNickname: String = "", val profileSaved: Boolean = false, val deleting: Boolean = false,
    val deleteEmail: String = "", val deletePassword: String = "", val deleted: Boolean = false,
    val qqNumber: String = "", val savedQqNumber: String = "", val avatar: Bitmap? = null,
    val avatarLoading: Boolean = false, val avatarFailed: Boolean = false, val qqInvalid: Boolean = false,
    val avatarSaving: Boolean = false, val avatarSaved: Boolean = false,
    val avatarSaveFailed: Boolean = false) {
    override fun toString() = "AuthUiState[redacted]"
}

class AuthViewModel(private val useCases: AuthUseCases, private val avatars: AvatarUseCases? = null) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState(account = useCases.account.value,
        profileNickname = useCases.account.value?.nickname.orEmpty()))
    val uiState = mutableState.asStateFlow()
    private var avatarPreference: Job? = null
    private var avatarLoad: Job? = null
    private var avatarSave: Job? = null
    private var avatarRequest = 0L
    private var avatarUser: Long? = null
    init {
        viewModelScope.launch { useCases.account.collect { account ->
            mutableState.update { it.copy(account = account, password = "", confirmation = "", deleting = if(account==null) false else it.deleting,
                deleteEmail = "", deletePassword = "",
                profileNickname = if (it.account != account) account?.nickname.orEmpty() else it.profileNickname,
                profileSaved = if (account == null) false else it.profileSaved) }
            if (avatarUser != account?.id) {
                avatarUser = account?.id
                avatarPreference?.cancel(); avatarLoad?.cancel(); avatarSave?.cancel()
                avatarRequest++
                mutableState.update { it.copy(qqNumber="", savedQqNumber="", avatar=null, avatarLoading=false,
                    avatarFailed=false, avatarSaved=false, qqInvalid=false, avatarSaving=false, avatarSaveFailed=false) }
                if (account != null && avatars != null) avatarPreference = viewModelScope.launch {
                    try {
                        avatars.observe(account.id).collect { saved ->
                            val number = saved ?: QqAvatarPolicy.fromEmail(account.email).orEmpty()
                            mutableState.update {
                                it.copy(qqNumber = if (it.avatarSaving || it.qqNumber == it.savedQqNumber) number else it.qqNumber,
                                    savedQqNumber=number)
                            }
                            loadAvatar(number)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { mutableState.update { it.copy(avatarFailed=true) } }
                }
            }
        } }
        viewModelScope.launch { useCases.status.collect { status -> mutableState.update { it.copy(status = status) } } }
    }
    fun qqNumber(value: String) {
        if (!uiState.value.avatarSaving) mutableState.update { it.copy(qqNumber=value.take(13), qqInvalid=false,
            avatarSaved=false, avatarSaveFailed=false) }
    }
    fun saveAvatar() {
        val snapshot = uiState.value
        val account = snapshot.account ?: return
        val number = snapshot.qqNumber.trim()
        if (number.isNotEmpty() && !QqAvatarPolicy.valid(number)) {
            mutableState.update { it.copy(qqInvalid=true) }; return
        }
        if (snapshot.avatarSaving || avatars == null) return
        mutableState.update { it.copy(avatarSaving=true, avatarSaveFailed=false, avatarSaved=false) }
        avatarSave = viewModelScope.launch {
            try {
                avatars.save(account.id, number)
                if (avatarUser == account.id) mutableState.update { it.copy(avatarSaved=true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (avatarUser == account.id) mutableState.update { it.copy(avatarSaveFailed=true) } }
            finally { if(avatarUser == account.id) mutableState.update { it.copy(avatarSaving=false) } }
        }
    }
    fun retryAvatar() = loadAvatar(uiState.value.savedQqNumber)
    private fun loadAvatar(number: String) {
        avatarLoad?.cancel()
        val request = ++avatarRequest
        mutableState.update { it.copy(avatar=null, avatarLoading=number.isNotEmpty(), avatarFailed=false) }
        val user = avatarUser
        if (number.isEmpty() || avatars == null) return
        avatarLoad = viewModelScope.launch {
            try {
                val bitmap = avatars.load(number)
                if (avatarUser == user && avatarRequest == request) mutableState.update { it.copy(avatar=bitmap, avatarLoading=false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (avatarUser == user && avatarRequest == request) mutableState.update { it.copy(avatarLoading=false, avatarFailed=true) } }
        }
    }
    fun email(value: String) = edit { it.copy(email = value.take(129)) }
    fun nickname(value: String) = edit { it.copy(nickname = value.take(65)) }
    fun profileNickname(value: String) = edit { it.copy(profileNickname = value.take(65), profileSaved = false) }
    fun saveProfile() {
        val value = mutableState.value.profileNickname
        perform { useCases.updateProfile(value); mutableState.update { it.copy(profileSaved = true) } }
    }
    fun password(value: String) = edit { it.copy(password = value.take(73)) }
    fun confirmation(value: String) = edit { it.copy(confirmation = value.take(73)) }
    fun toggleMode() = edit { it.copy(registering = !it.registering, password = "", confirmation = "") }
    fun clearCredentials() { mutableState.update { it.copy(password = "", confirmation = "", deletePassword = "", deleting = false) } }
    fun showDeletion(value: Boolean) = edit { it.copy(deleting=value, deleteEmail="",deletePassword="", deleted=false) }
    fun deleteEmail(value: String) = edit { it.copy(deleteEmail=value.take(129)) }
    fun deletePassword(value: String) = edit { it.copy(deletePassword=value.take(73)) }
    fun deleteAccount() {
        val snapshot=uiState.value
        perform { useCases.deleteAccount(snapshot.deleteEmail,snapshot.deletePassword)
            mutableState.update { it.copy(deleting=false,deleted=true) } }
    }
    private fun edit(change: (AuthUiState) -> AuthUiState) {
        if (!mutableState.value.busy) mutableState.update { change(it).copy(failure = null, failureDetail = null) }
    }

    fun submit() {
        val state = mutableState.value
        if (state.busy) return
        perform {
            if (state.registering) useCases.register(state.email, state.password, state.confirmation, state.nickname)
            else useCases.login(state.email, state.password)
        }
    }
    fun logout() = perform { useCases.logout() }
    fun restore() = perform { useCases.restore() }
    private fun perform(operation: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, failure = null, failureDetail = null) }
        viewModelScope.launch {
            try { operation(); clearCredentials()
            } catch (exception: CancellationException) { throw exception
            } catch (exception: AuthException) { mutableState.update { it.copy(failure = exception.failure, failureDetail = exception.detail) }
            } catch (_: Exception) { mutableState.update { it.copy(failure = AuthFailure.SERVICE) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }
    companion object {
        fun factory(useCases: AuthUseCases, avatars: AvatarUseCases? = null) = viewModelFactory { initializer { AuthViewModel(useCases, avatars) } }
    }
}
