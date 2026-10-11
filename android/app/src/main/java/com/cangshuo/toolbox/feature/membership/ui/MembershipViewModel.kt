package com.cangshuo.toolbox.feature.membership.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.membership.data.MembershipDto
import com.cangshuo.toolbox.feature.membership.data.RemoteMembershipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class MembershipUiState(val loading: Boolean = false, val value: MembershipDto? = null, val failed: Boolean = false) {
    override fun toString() = "MembershipUiState[redacted]"
}

/** Reloads membership whenever the signed-in account changes; signed-out clears the row. */
class MembershipViewModel(repository: RemoteMembershipRepository, users: Flow<Long?>) : ViewModel() {
    private val mutable = MutableStateFlow(MembershipUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            users.distinctUntilChanged().collect { userId ->
                if (userId == null) {
                    mutable.value = MembershipUiState()
                    return@collect
                }
                mutable.value = MembershipUiState(loading = true)
                val value = repository.status()
                mutable.value = MembershipUiState(loading = false, value = value, failed = value == null)
            }
        }
    }

    companion object {
        fun factory(repository: RemoteMembershipRepository, users: Flow<Long?>) = viewModelFactory {
            initializer { MembershipViewModel(repository, users) }
        }
    }
}
