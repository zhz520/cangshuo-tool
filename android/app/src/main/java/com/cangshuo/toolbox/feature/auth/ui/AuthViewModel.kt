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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(val account: AccountProfile? = null, val registering: Boolean = false,
    val email: String = "", val nickname: String = "", val password: String = "", val confirmation: String = "",
    val busy: Boolean = false, val failure: AuthFailure? = null, val status: AuthStatus = AuthStatus.SIGNED_OUT,
    val profileNickname: String = "", val profileSaved: Boolean = false) {
    override fun toString() = "AuthUiState[redacted]"
}

class AuthViewModel(private val useCases: AuthUseCases) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState(account = useCases.account.value))
    val uiState = mutableState.asStateFlow()
    init {
        viewModelScope.launch { useCases.account.collect { account ->
            mutableState.update { it.copy(account = account, password = "", confirmation = "",
                profileNickname = if (it.account != account) account?.nickname.orEmpty() else it.profileNickname,
                profileSaved = if (account == null) false else it.profileSaved) }
        } }
        viewModelScope.launch { useCases.status.collect { status -> mutableState.update { it.copy(status = status) } } }
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
    fun clearCredentials() { mutableState.update { it.copy(password = "", confirmation = "") } }
    private fun edit(change: (AuthUiState) -> AuthUiState) {
        if (!mutableState.value.busy) mutableState.update { change(it).copy(failure = null) }
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
        mutableState.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            try { operation(); clearCredentials()
            } catch (exception: CancellationException) { throw exception
            } catch (exception: AuthException) { mutableState.update { it.copy(failure = exception.failure) }
            } catch (_: Exception) { mutableState.update { it.copy(failure = AuthFailure.SERVICE) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }
    companion object {
        fun factory(useCases: AuthUseCases) = viewModelFactory { initializer { AuthViewModel(useCases) } }
    }
}
