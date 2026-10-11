package com.cangshuo.toolbox.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxPrivacyInfo
import com.cangshuo.toolbox.core.ui.ToolboxPermissionInfo
import com.cangshuo.toolbox.core.ui.ToolboxLicensesInfo
import com.cangshuo.toolbox.feature.diagnostics.ui.DiagnosticsInfo
import android.text.format.Formatter
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolListItem
import com.cangshuo.toolbox.core.ui.ToolSettingItem
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import com.cangshuo.toolbox.core.ui.ToolboxChevron
import com.cangshuo.toolbox.core.ui.ToolboxScaffold
import com.cangshuo.toolbox.feature.sync.ui.SettingsRoute

/** Full-screen settings page: appearance / startup (reused) + general rows. */
@Composable
fun SettingsScreen(
    syncFactory: ViewModelProvider.Factory,
    factory: ViewModelProvider.Factory,
    diagnosticsFactory: ViewModelProvider.Factory,
    accentFactory: ViewModelProvider.Factory,
    onClose: () -> Unit,
    onShowUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val model: SettingsViewModel = viewModel(factory=factory)
    val cache by model.state.collectAsStateWithLifecycle()
    val accentModel: AccentViewModel = viewModel(key = "settings.accent", factory = accentFactory)
    val accent by accentModel.accent.collectAsStateWithLifecycle()
    var confirmCache by remember {mutableStateOf(false)}
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    ToolboxScaffold(title = stringResource(R.string.profile_settings), onBack = onClose) { modifier ->
        Box(modifier, contentAlignment=Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max=ToolboxDimens.formMaxWidth).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ToolboxDimens.pageGutter, vertical = ToolboxDimens.pageTop),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                ToolSectionHeader(stringResource(R.string.settings_title))
                SettingsRoute(syncFactory, accent, accentModel::select)
            }
            Column {
                ToolSectionHeader(stringResource(R.string.settings_general))
                ToolSettingItem(
                    title = stringResource(R.string.settings_cache),
                    icon = R.drawable.ic_tools,
                    value = when {
                        cache.busy -> stringResource(R.string.settings_cache_working)
                        cache.failed -> stringResource(R.string.settings_cache_failed)
                        cache.cleared -> stringResource(R.string.settings_cache_cleared)
                        else -> cache.sizeBytes?.let {Formatter.formatShortFileSize(context,it)}
                    },
                    onClick = {confirmCache=true},
                    loading = cache.busy,
                )
                ToolboxPrivacyInfo(asListItem=true)
                ToolboxPermissionInfo(asListItem=true)
                ToolboxLicensesInfo(asListItem=true)
                ToolListItem(
                    title = stringResource(R.string.settings_terms),
                    icon = R.drawable.ic_info,
                    onClick = onShowUnavailable,
                    trailing = { ToolboxChevron() },
                )
                ToolListItem(
                    title = stringResource(R.string.settings_about),
                    icon = R.drawable.ic_info,
                    onClick = onShowUnavailable,
                    trailing = { ToolboxChevron() },
                    showDivider = false,
                )
            }
            DiagnosticsInfo(diagnosticsFactory,asListItem=true)
            Text(
                text = stringResource(R.string.settings_version, versionName),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        }
    }
    if(confirmCache) AlertDialog(onDismissRequest={confirmCache=false},
        title={Text(stringResource(R.string.settings_cache_confirm))},
        text={Text(stringResource(R.string.settings_cache_description))},
        confirmButton={ToolboxTextButton(onClick={confirmCache=false;model.clear()}) {Text(stringResource(R.string.settings_cache))}},
        dismissButton={ToolboxTextButton(onClick={confirmCache=false}) {Text(stringResource(R.string.action_cancel))}})
}
