package com.cangshuo.toolbox.feature.battery.ui

import android.os.BatteryManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import java.util.Locale

@Composable
fun BatteryInfoRoute(factory: ViewModelProvider.Factory) {
    val model: BatteryInfoViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) { model.start(); onStopOrDispose { model.stop() } }
    val unknown = stringResource(R.string.device_info_unknown)
    fun yesNo(value: Boolean?) = when(value) { true -> R.string.storage_yes; false -> R.string.storage_no; null -> R.string.device_info_unknown }
    val rows = state.snapshot?.let { b -> buildList {
        add(stringResource(R.string.battery_present) to stringResource(yesNo(b.present)))
        add(stringResource(R.string.battery_level) to (b.percent?.let { String.format(Locale.ROOT,"%.1f%%",it) } ?: unknown))
        add(stringResource(R.string.battery_status) to stringResource(when(b.status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> R.string.battery_charging
            BatteryManager.BATTERY_STATUS_DISCHARGING -> R.string.battery_discharging
            BatteryManager.BATTERY_STATUS_FULL -> R.string.battery_full
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.battery_not_charging
            else -> R.string.device_info_unknown
        }))
        val sources = if (b.plugged == 0) listOf(stringResource(R.string.battery_unplugged)) else buildList {
            if (b.plugged != null) {
                if (b.plugged and 1 != 0) add(stringResource(R.string.battery_ac))
                if (b.plugged and 2 != 0) add("USB")
                if (b.plugged and 4 != 0) add(stringResource(R.string.battery_wireless))
                if (b.plugged and 8 != 0) add(stringResource(R.string.battery_dock))
                if (b.plugged < 0 || b.plugged and 15.inv() != 0) add(unknown)
            }
        }
        add(stringResource(R.string.battery_source) to sources.joinToString(" / ").ifEmpty { unknown })
        add(stringResource(R.string.battery_health) to stringResource(when(b.health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> R.string.battery_health_good
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.battery_health_hot
            BatteryManager.BATTERY_HEALTH_DEAD -> R.string.battery_health_dead
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.battery_health_voltage
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> R.string.battery_health_failure
            BatteryManager.BATTERY_HEALTH_COLD -> R.string.battery_health_cold
            else -> R.string.device_info_unknown
        }))
        add(stringResource(R.string.battery_temperature) to (b.temperatureC?.let { String.format(Locale.ROOT,"%.1f °C",it) } ?: unknown))
        add(stringResource(R.string.battery_voltage) to (b.voltageMv?.let { "$it mV" } ?: unknown))
        add(stringResource(R.string.battery_technology) to (b.technology ?: unknown))
        add(stringResource(R.string.battery_current) to (b.currentUa?.let { String.format(Locale.ROOT,"%.2f mA",it/1000.0) } ?: unknown))
        add(stringResource(R.string.battery_charge) to (b.chargeUah?.let { String.format(Locale.ROOT,"%.2f mAh",it/1000.0) } ?: unknown))
        add(stringResource(R.string.battery_power_save) to stringResource(yesNo(b.powerSave)))
    }}.orEmpty()
    val note = stringResource(R.string.battery_note)
    val report = rows.takeIf { it.isNotEmpty() }?.joinToString("\n", postfix = "\n\n$note") { "${it.first}: ${it.second}" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SystemReportActions(report, state.loading, model::refresh)
        if (state.loading && state.snapshot == null) ToolboxLoadingState(stringResource(R.string.battery_loading))
        if (state.failed || state.snapshot?.available == false) Text(stringResource(R.string.battery_error), color = MaterialTheme.colorScheme.error)
        if (rows.isNotEmpty()) SystemReportCard(stringResource(R.string.battery_name), rows)
    }
}
