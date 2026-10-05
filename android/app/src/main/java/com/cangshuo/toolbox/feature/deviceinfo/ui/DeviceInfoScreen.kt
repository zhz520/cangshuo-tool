package com.cangshuo.toolbox.feature.deviceinfo.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.feature.deviceinfo.domain.*
import kotlinx.coroutines.launch

@Composable
fun DeviceInfoRoute(factory: ViewModelProvider.Factory) {
    val model: DeviceInfoViewModel=viewModel(factory=factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) { model.refresh();onStopOrDispose { } }
    DeviceInfoScreen(state,model::refresh)
}
@Composable
private fun DeviceInfoScreen(state: DeviceInfoUiState,onRefresh: ()->Unit) {
    val clipboard=LocalClipboard.current
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var actionFailed by remember { mutableStateOf(false) }
    val unknown=stringResource(R.string.device_info_unknown)
    val labels=DeviceField.entries.associateWith { stringResource(it.label()) }
    val report=state.snapshot?.let { snapshot -> DeviceField.entries.joinToString("\n") { "${labels.getValue(it)}: ${snapshot.values[it] ?: unknown}" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.device_info_privacy),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=onRefresh,enabled=!state.loading) { Text(stringResource(R.string.device_info_refresh)) }
            OutlinedButton(onClick={scope.launch { try { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Device info",report))) }
                catch(_: Exception) { actionFailed=true } }},enabled=report!=null) { Text(stringResource(R.string.device_info_copy)) }
            OutlinedButton(onClick={ try {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,report) },null))
            } catch(_: Exception) { actionFailed=true } },enabled=report!=null) { Text(stringResource(R.string.device_info_share)) }
        }
        if(state.loading) ToolboxLoadingState(stringResource(R.string.device_info_loading))
        if(state.failed || actionFailed) Text(stringResource(R.string.device_info_error),color=MaterialTheme.colorScheme.error)
        state.snapshot?.let { snapshot ->
            DeviceField.entries.forEach { field ->
                Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(labels.getValue(field),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer { Text(snapshot.values[field] ?: unknown,style=MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
        }
    }
}
private fun DeviceField.label()=when(this) {
    DeviceField.MANUFACTURER->R.string.device_info_manufacturer;DeviceField.BRAND->R.string.device_info_brand
    DeviceField.MODEL->R.string.device_info_model;DeviceField.DEVICE->R.string.device_info_device
    DeviceField.PRODUCT->R.string.device_info_product;DeviceField.HARDWARE->R.string.device_info_hardware
    DeviceField.SOC->R.string.device_info_soc;DeviceField.ANDROID->R.string.device_info_android
    DeviceField.API->R.string.device_info_api;DeviceField.SECURITY_PATCH->R.string.device_info_patch
    DeviceField.BUILD->R.string.device_info_build;DeviceField.ABI->R.string.device_info_abi
    DeviceField.PROCESSORS->R.string.device_info_processors;DeviceField.TOTAL_RAM->R.string.device_info_ram
    DeviceField.AVAILABLE_RAM->R.string.device_info_available_ram;DeviceField.UPTIME->R.string.device_info_uptime
    DeviceField.PANEL->R.string.device_info_panel;DeviceField.REFRESH_RATE->R.string.device_info_refresh_rate
    DeviceField.DENSITY->R.string.device_info_density;DeviceField.GLES->R.string.device_info_gles
    DeviceField.APP->R.string.device_info_app
}
