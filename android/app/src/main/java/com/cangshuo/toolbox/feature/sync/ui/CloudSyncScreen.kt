package com.cangshuo.toolbox.feature.sync.ui

import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolListItem
import com.cangshuo.toolbox.core.ui.ToolSettingItem
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.ToolboxChevron
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import com.cangshuo.toolbox.ui.theme.ToolboxAccent
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
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        ToolSectionHeader(stringResource(R.string.sync_title))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.sync_favorites),modifier=Modifier.weight(1f))
            ToolboxSwitch(checked=state.context.preference.enabled,onCheckedChange=model::configure,enabled=!state.busy)
        }
        Text(stringResource(R.string.sync_favorites_privacy),style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        ToolboxDivider(startIndent=0.dp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.sync_recent),modifier=Modifier.weight(1f))
            ToolboxSwitch(checked=state.context.preference.recentEnabled,onCheckedChange=model::recent,enabled=!state.busy)
        }
        Text(stringResource(R.string.sync_recent_privacy),style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        ToolboxDivider(startIndent=0.dp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.sync_settings),modifier=Modifier.weight(1f))
            ToolboxSwitch(checked=state.context.preference.settingsEnabled,onCheckedChange=model::settings,enabled=!state.busy)
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
            style=MaterialTheme.typography.bodySmall,
            color=if (state.error || state.status==SyncStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.context.preference.types.isNotEmpty()) ToolboxOutlinedButton(onClick=model::sync,
            enabled=!state.busy && state.status!=SyncStatus.SYNCING) {
            if (state.status==SyncStatus.SYNCING) ToolboxLoadingIndicator(compact=true)
            Text(stringResource(R.string.sync_now))
        }
    }
}

@Composable
fun SettingsRoute(factory: ViewModelProvider.Factory, accent: ToolboxAccent, onAccentSelected: (ToolboxAccent) -> Unit) {
    val model: CloudSyncViewModel=viewModel(key="account.sync",factory=factory)
    val state by model.state.collectAsStateWithLifecycle()
    Column {
        SettingPicker(R.drawable.ic_settings,R.string.settings_theme,state.settings.theme,listOf("SYSTEM" to R.string.settings_system,
            "LIGHT" to R.string.settings_light,"DARK" to R.string.settings_dark),!state.busy) { model.setting("theme",it) }
        AccentPicker(accent,!state.busy,onAccentSelected)
        SettingPicker(R.drawable.ic_text,R.string.settings_language,state.settings.language,listOf("SYSTEM" to R.string.settings_system,
            "zh-CN" to R.string.settings_zh,"en" to R.string.settings_en),!state.busy) { model.setting("language",it) }
        SettingPicker(R.drawable.ic_tools,R.string.settings_columns,if(state.settings.gridColumns==0) "AUTO" else state.settings.gridColumns.toString(),
            listOf("AUTO" to R.string.settings_auto,"1" to R.string.settings_columns_one,"2" to R.string.settings_columns_two,
                "3" to R.string.settings_columns_three),!state.busy) { model.setting("grid_columns",it) }
        SettingPicker(R.drawable.ic_home,R.string.settings_startup,state.settings.startupPage,listOf("HOME" to R.string.nav_home,
            "TOOLS" to R.string.nav_tools,"FAVORITES" to R.string.nav_favorites,"PROFILE" to R.string.nav_profile),!state.busy) {
            model.setting("startup_page",it)
        }
        Text(stringResource(R.string.settings_hint),modifier=Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.error) Text(stringResource(R.string.settings_error),color=MaterialTheme.colorScheme.error)
    }
}
@Composable
private fun AccentPicker(accent: ToolboxAccent, enabled: Boolean, onSelected: (ToolboxAccent) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Swatch dots need the variant that stays visible on the current surface.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    ToolSettingItem(
        title=stringResource(R.string.settings_accent),
        value=stringResource(accent.labelResource()),
        icon=R.drawable.ic_palette,
        valueColor=MaterialTheme.colorScheme.primary,
        onClick={ expanded=true },
        enabled=enabled,
    )
    if(expanded) AlertDialog(
        onDismissRequest={expanded=false},
        title={Text(stringResource(R.string.settings_accent))},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                ToolboxAccent.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected=option==accent,enabled=enabled,role=Role.RadioButton,
                            onClick={expanded=false;onSelected(option)}).padding(vertical=8.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected=option==accent,onClick=null,enabled=enabled)
                        Box(Modifier.size(14.dp).clip(CircleShape).background(if (darkTheme) option.dark else option.light))
                        Text(stringResource(option.labelResource()),modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton={ToolboxTextButton(onClick={expanded=false}) {Text(stringResource(R.string.action_cancel))}},
    )
}
private fun ToolboxAccent.labelResource(): Int = when (this) {
    ToolboxAccent.BLUE -> R.string.accent_blue
    ToolboxAccent.TEAL -> R.string.accent_teal
    ToolboxAccent.VIOLET -> R.string.accent_violet
    ToolboxAccent.ORANGE -> R.string.accent_orange
    ToolboxAccent.PINK -> R.string.accent_pink
    ToolboxAccent.BLACK -> R.string.accent_black
}
@Composable
private fun SettingPicker(icon: Int,label: Int,value: String,options: List<Pair<String,Int>>,enabled: Boolean,onChange: (String)->Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ToolSettingItem(
        title=stringResource(label),
        value=stringResource((options.firstOrNull { it.first==value } ?: options.first()).second),
        icon=icon,
        onClick={ expanded=true },
        enabled=enabled,
    )
    if(expanded) AlertDialog(
        onDismissRequest={expanded=false},
        title={Text(stringResource(label))},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                options.forEach { (key,text) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected=key==value,enabled=enabled,role=Role.RadioButton,
                            onClick={expanded=false;onChange(key)}).padding(vertical=8.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected=key==value,onClick=null,enabled=enabled)
                        Text(stringResource(text),modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton={ToolboxTextButton(onClick={expanded=false}) {Text(stringResource(R.string.action_cancel))}},
    )
}
