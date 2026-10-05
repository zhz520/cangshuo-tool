package com.cangshuo.toolbox.feature.sync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.feature.sync.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CloudSyncUiState(val context: SyncContext=SyncContext(),val status: SyncStatus=SyncStatus.IDLE,
    val settings: AppSettings=AppSettings(),val busy: Boolean=false,val error: Boolean=false)
class CloudSyncViewModel(private val useCases: CloudSyncUseCases) : ViewModel() {
    private val mutable=MutableStateFlow(CloudSyncUiState())
    val state=mutable.asStateFlow()
    init { viewModelScope.launch { combine(useCases.context,useCases.progress,useCases.settings) { context,progress,settings ->
        Triple(context,if (context.userId==progress.userId) progress.status else SyncStatus.IDLE,settings)
    }.collect { (context,status,settings) -> mutable.update { it.copy(context=context,status=status,settings=settings) } } } }
    fun configure(enabled: Boolean) = perform { useCases.configure(enabled) }
    fun recent(enabled: Boolean) = perform { useCases.configure(enabled,"RECENT") }
    fun settings(enabled: Boolean) = perform { useCases.configure(enabled,"SETTING") }
    fun setting(key: String,value: String) = perform { useCases.setSetting(key,value) }
    fun sync() = perform { useCases.sync() }
    private fun perform(block: suspend ()->Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy=true,error=false) }
        viewModelScope.launch {
            try { block() } catch(e: CancellationException) { throw e }
            catch(_: Exception) { mutable.update { it.copy(error=true) } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    companion object { fun factory(useCases: CloudSyncUseCases)=viewModelFactory { initializer { CloudSyncViewModel(useCases) } } }
}
@Composable
fun CloudSyncRoute(factory: ViewModelProvider.Factory) {
    val model: CloudSyncViewModel=viewModel(key="account.sync",factory=factory)
    val state by model.state.collectAsStateWithLifecycle()
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.sync_title),style=MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(stringResource(R.string.sync_favorites),modifier=Modifier.weight(1f).padding(top=12.dp))
                Switch(checked=state.context.preference.enabled,onCheckedChange=model::configure,enabled=!state.busy)
            }
            Text(stringResource(R.string.sync_favorites_privacy),style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(stringResource(R.string.sync_recent),modifier=Modifier.weight(1f).padding(top=12.dp))
                Switch(checked=state.context.preference.recentEnabled,onCheckedChange=model::recent,enabled=!state.busy)
            }
            Text(stringResource(R.string.sync_recent_privacy),style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(stringResource(R.string.sync_settings),modifier=Modifier.weight(1f).padding(top=12.dp))
                Switch(checked=state.context.preference.settingsEnabled,onCheckedChange=model::settings,enabled=!state.busy)
            }
            Text(stringResource(R.string.sync_settings_privacy),style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            val label=when(state.status) {
                SyncStatus.IDLE->if(state.context.preference.types.isEmpty()) R.string.sync_idle else R.string.sync_pending
                SyncStatus.SYNCING->R.string.sync_working
                SyncStatus.SUCCESS->R.string.sync_success
                SyncStatus.ERROR->R.string.sync_error
            }
            Text(stringResource(if (state.error) R.string.sync_error else label),
                color=if (state.error || state.status==SyncStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.context.preference.types.isNotEmpty()) OutlinedButton(onClick=model::sync,
                enabled=!state.busy && state.status!=SyncStatus.SYNCING) {
                if (state.status==SyncStatus.SYNCING) ToolboxLoadingIndicator(compact=true)
                Text(stringResource(R.string.sync_now))
            }
        }
    }
}

@Composable
fun SettingsRoute(factory: ViewModelProvider.Factory) {
    val model: CloudSyncViewModel=viewModel(key="account.sync",factory=factory)
    val state by model.state.collectAsStateWithLifecycle()
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_title),style=MaterialTheme.typography.titleLarge)
            SettingPicker(R.string.settings_theme,state.settings.theme,listOf("SYSTEM" to R.string.settings_system,
                "LIGHT" to R.string.settings_light,"DARK" to R.string.settings_dark),!state.busy) { model.setting("theme",it) }
            SettingPicker(R.string.settings_language,state.settings.language,listOf("SYSTEM" to R.string.settings_system,
                "zh-CN" to R.string.settings_zh,"en" to R.string.settings_en),!state.busy) { model.setting("language",it) }
            SettingPicker(R.string.settings_columns,if(state.settings.gridColumns==0) "AUTO" else state.settings.gridColumns.toString(),
                listOf("AUTO" to R.string.settings_auto,"1" to R.string.settings_columns_one,"2" to R.string.settings_columns_two,
                    "3" to R.string.settings_columns_three),!state.busy) { model.setting("grid_columns",it) }
            SettingPicker(R.string.settings_startup,state.settings.startupPage,listOf("HOME" to R.string.nav_home,
                "TOOLS" to R.string.nav_tools,"FAVORITES" to R.string.nav_favorites,"PROFILE" to R.string.nav_profile),!state.busy) {
                model.setting("startup_page",it)
            }
            Text(stringResource(R.string.settings_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.error) Text(stringResource(R.string.settings_error),color=MaterialTheme.colorScheme.error)
        }
    }
}
@Composable
private fun SettingPicker(label: Int,value: String,options: List<Pair<String,Int>>,enabled: Boolean,onChange: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(label),style=MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) {
                Text(stringResource(options.first { it.first==value }.second))
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                options.forEach { (key,text) -> DropdownMenuItem(text={Text(stringResource(text))},
                    onClick={expanded=false;onChange(key)}) }
            }
        }
    }
}
