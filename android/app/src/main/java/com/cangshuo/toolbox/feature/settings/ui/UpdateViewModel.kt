package com.cangshuo.toolbox.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.settings.data.AppVersionDto
import com.cangshuo.toolbox.feature.settings.data.RemoteUpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(val checking: Boolean = false, val result: AppVersionDto? = null, val failed: Boolean = false) {
    override fun toString() = "UpdateUiState[redacted]"
}

/** Manual update check against the anonymous /app/version endpoint. */
class UpdateViewModel(private val repository: RemoteUpdateRepository, private val currentVersionCode: Long) : ViewModel() {
    private val mutable = MutableStateFlow(UpdateUiState())
    val state = mutable.asStateFlow()

    fun check() {
        if (mutable.value.checking) return
        mutable.update { it.copy(checking = true, result = null, failed = false) }
        viewModelScope.launch {
            val latest = repository.latest()
            mutable.update { it.copy(checking = false, result = latest, failed = latest == null) }
        }
    }

    fun isNewer(result: AppVersionDto): Boolean = result.versionCode > currentVersionCode

    fun clear() = mutable.update { it.copy(result = null, failed = false) }

    companion object {
        fun factory(repository: RemoteUpdateRepository, currentVersionCode: Long) = viewModelFactory {
            initializer { UpdateViewModel(repository, currentVersionCode) }
        }
    }
}
