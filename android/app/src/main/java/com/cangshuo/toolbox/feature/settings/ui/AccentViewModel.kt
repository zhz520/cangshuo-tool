package com.cangshuo.toolbox.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.feature.settings.data.AccentPreferenceRepository
import com.cangshuo.toolbox.ui.theme.ToolboxAccent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Local accent choice used by the settings row; MainActivity observes the same store. */
class AccentViewModel(private val repository: AccentPreferenceRepository) : ViewModel() {
    val accent = repository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, ToolboxAccent.BLUE)

    fun select(value: ToolboxAccent) {
        viewModelScope.launch { repository.save(value) }
    }

    companion object {
        fun factory(repository: AccentPreferenceRepository) = viewModelFactory {
            initializer { AccentViewModel(repository) }
        }
    }
}
