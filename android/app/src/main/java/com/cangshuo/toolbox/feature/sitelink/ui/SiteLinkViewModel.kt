package com.cangshuo.toolbox.feature.sitelink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.sitelink.data.RemoteSiteLinkRepository
import com.cangshuo.toolbox.feature.sitelink.data.SiteLinkDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SiteLinkUiState(
    val loading: Boolean = false,
    val links: List<SiteLinkDto> = emptyList(),
    val failed: Boolean = false,
    val title: String = "",
    val url: String = "",
    val description: String = "",
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val submitFailed: Boolean = false,
) {
    override fun toString() = "SiteLinkUiState[redacted]"
}

class SiteLinkViewModel(private val repository: RemoteSiteLinkRepository) : ViewModel() {
    private val mutable = MutableStateFlow(SiteLinkUiState())
    val state = mutable.asStateFlow()

    fun refresh() {
        if (mutable.value.loading) return
        mutable.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val links = repository.list()
            mutable.update { it.copy(loading = false, links = links.orEmpty(), failed = links == null) }
        }
    }

    fun title(value: String) = mutable.update { it.copy(title = value.take(100), submitFailed = false, submitted = false) }
    fun url(value: String) = mutable.update { it.copy(url = value.take(500), submitFailed = false, submitted = false) }
    fun description(value: String) = mutable.update { it.copy(description = value.take(300), submitFailed = false, submitted = false) }

    fun submit() {
        val snapshot = mutable.value
        if (snapshot.submitting) return
        if (snapshot.title.isBlank() || snapshot.url.isBlank()) {
            mutable.update { it.copy(submitFailed = true) }
            return
        }
        mutable.update { it.copy(submitting = true, submitFailed = false, submitted = false) }
        viewModelScope.launch {
            val accepted = repository.submit(snapshot.title.trim(), snapshot.url.trim(), snapshot.description.trim())
            mutable.update {
                it.copy(submitting = false, submitted = accepted, submitFailed = !accepted,
                    title = if (accepted) "" else it.title, url = if (accepted) "" else it.url,
                    description = if (accepted) "" else it.description)
            }
        }
    }

    fun clearFlags() = mutable.update { it.copy(submitted = false, submitFailed = false) }

    companion object {
        fun factory(repository: RemoteSiteLinkRepository) = viewModelFactory {
            initializer { SiteLinkViewModel(repository) }
        }
    }
}
