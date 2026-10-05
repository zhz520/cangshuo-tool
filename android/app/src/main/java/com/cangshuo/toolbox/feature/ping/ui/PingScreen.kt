package com.cangshuo.toolbox.feature.ping.ui

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
import com.cangshuo.toolbox.feature.ping.domain.*
import java.util.Locale

@Composable
fun PingRoute(factory: ViewModelProvider.Factory) {
    val model: PingViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) { onStopOrDispose { model.cancel() } }
    val note = stringResource(R.string.ping_note)
    val unknown = stringResource(R.string.device_info_unknown)
    fun ms(value: Double?) = value?.let { String.format(Locale.ROOT,"%.3f ms",it) } ?: unknown
    val rows = state.result?.let { result -> listOf(
        stringResource(R.string.ping_host) to state.host,
        stringResource(R.string.ping_address) to (result.address ?: unknown),
        stringResource(R.string.ping_sent) to result.transmitted.toString(),
        stringResource(R.string.ping_received) to result.received.toString(),
        stringResource(R.string.ping_loss) to String.format(Locale.ROOT,"%.1f%%",result.lossPercent),
        stringResource(R.string.ping_min) to ms(result.minMs),stringResource(R.string.ping_average) to ms(result.averageMs),stringResource(R.string.ping_max) to ms(result.maxMs)
    ) }.orEmpty()
    val report = rows.takeIf { it.isNotEmpty() }?.joinToString("\n",postfix = "\n\n$note") { "${it.first}: ${it.second}" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(note,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = state.host,onValueChange = model::host,label = { Text(stringResource(R.string.ping_host)) },
            modifier = Modifier.fillMaxWidth(),singleLine = true,enabled = !state.running,isError = state.error == PingError.INVALID_TARGET)
        Row { Switch(checked = state.ipv6,onCheckedChange = model::ipv6,enabled = !state.running);Text(stringResource(R.string.ping_ipv6),Modifier.padding(12.dp)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1,4,8).forEach { count -> FilterChip(selected = state.count == count,onClick = { model.count(count) },enabled = !state.running,label = { Text(stringResource(R.string.ping_count,count)) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = model::run,enabled = !state.running) { Text(stringResource(R.string.ping_run)) }
            OutlinedButton(onClick = model::cancel,enabled = state.running) { Text(stringResource(R.string.ping_cancel)) }
        }
        if (state.running) ToolboxLoadingState(stringResource(R.string.ping_running))
        if (state.cancelled) Text(stringResource(R.string.ping_cancelled))
        state.error?.let { error -> Text(stringResource(when(error) {
            PingError.INVALID_TARGET -> R.string.ping_invalid;PingError.BUSY -> R.string.ping_busy;PingError.TIMEOUT -> R.string.ping_timeout
            PingError.DNS -> R.string.ping_dns;PingError.UNSUPPORTED -> R.string.ping_unsupported
            PingError.OUTPUT -> R.string.ping_output;PingError.FAILED -> R.string.ping_error
        }),color = MaterialTheme.colorScheme.error) }
        if (rows.isNotEmpty()) {
            SystemReportCard(stringResource(R.string.ping_name),rows)
            if (state.result?.received == 0) Text(stringResource(R.string.ping_no_replies))
            state.result?.replies?.forEach { reply -> Text("#${reply.sequence}  ${if (reply.lessThan) "<" else ""}${ms(reply.timeMs)}") }
            SystemReportActions(report,false,model::run,showRefresh = false)
        }
    }
}
