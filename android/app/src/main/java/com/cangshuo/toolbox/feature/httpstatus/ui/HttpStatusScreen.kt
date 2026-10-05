package com.cangshuo.toolbox.feature.httpstatus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.SystemReportActions
import com.cangshuo.toolbox.core.ui.SystemReportCard
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.feature.httpstatus.domain.*
import java.util.Locale

@Composable
fun HttpStatusRoute(factory: ViewModelProvider.Factory) {
    val model: HttpStatusViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) { onStopOrDispose { model.cancel() } }
    val note = stringResource(R.string.http_status_note)
    val unknown = stringResource(R.string.device_info_unknown)
    val redirectsLabel = stringResource(R.string.http_status_redirects)
    val headersLabel = stringResource(R.string.http_status_headers)
    val result = state.result
    val rows = result?.let { r -> listOf(
        stringResource(R.string.http_status_final_url) to r.finalUrl,
        stringResource(R.string.http_status_status_code) to r.status.toString(),
        stringResource(R.string.http_status_status_text) to (r.statusText ?: unknown),
        stringResource(R.string.http_status_version) to (r.httpVersion ?: unknown),
        stringResource(R.string.http_status_elapsed) to String.format(Locale.ROOT, "%d ms", r.elapsedMs),
        redirectsLabel to r.redirects.size.toString(),
        stringResource(R.string.http_status_tls) to (r.tlsVersion ?: unknown),
        stringResource(R.string.http_status_cipher) to (r.tlsCipher ?: unknown),
        stringResource(R.string.http_status_server) to (r.server ?: unknown),
        stringResource(R.string.http_status_content_type) to (r.contentType ?: unknown),
        stringResource(R.string.http_status_content_length) to (r.contentLength?.toString() ?: unknown),
    ) }.orEmpty()
    val headerRows = result?.headers.orEmpty().map { it.name to it.value }
    val redirectRows = result?.redirects.orEmpty().map { it.status.toString() to "${it.from} → ${it.to}" }
    val report = if (rows.isEmpty()) null else buildString {
        append(rows.joinToString("\n") { "${it.first}: ${it.second}" })
        if (redirectRows.isNotEmpty()) {
            append("\n\n").append(redirectsLabel).append("\n")
            append(redirectRows.joinToString("\n") { "${it.first}: ${it.second}" })
        }
        if (headerRows.isNotEmpty()) {
            append("\n\n").append(headersLabel).append("\n")
            append(headerRows.joinToString("\n") { "${it.first}: ${it.second}" })
        }
        append("\n\n").append(note)
    }
    val statusColor = result?.let { r -> when (outcomeOf(r.status)) {
        HttpOutcome.SUCCESS -> MaterialTheme.colorScheme.tertiary
        HttpOutcome.REDIRECT -> MaterialTheme.colorScheme.primary
        HttpOutcome.CLIENT_ERROR, HttpOutcome.SERVER_ERROR -> MaterialTheme.colorScheme.error
        HttpOutcome.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = state.url, onValueChange = model::url, label = { Text(stringResource(R.string.http_status_url)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.running,
            isError = state.error == HttpError.INVALID_URL || state.error == HttpError.UNSUPPORTED_SCHEME)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.method == HttpMethod.HEAD, onClick = { model.method(HttpMethod.HEAD) },
                enabled = !state.running, label = { Text(stringResource(R.string.http_status_method_head)) })
            FilterChip(selected = state.method == HttpMethod.GET, onClick = { model.method(HttpMethod.GET) },
                enabled = !state.running, label = { Text(stringResource(R.string.http_status_method_get)) })
        }
        Row {
            Switch(checked = state.followRedirects, onCheckedChange = model::followRedirects, enabled = !state.running)
            Text(stringResource(R.string.http_status_follow), Modifier.padding(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = model::run, enabled = !state.running) { Text(stringResource(R.string.http_status_run)) }
            OutlinedButton(onClick = model::cancel, enabled = state.running) { Text(stringResource(R.string.http_status_cancel)) }
        }
        if (state.running) ToolboxLoadingState(stringResource(R.string.http_status_running))
        if (state.cancelled) Text(stringResource(R.string.http_status_cancelled))
        state.error?.let { error -> Text(stringResource(when (error) {
            HttpError.INVALID_URL -> R.string.http_status_invalid
            HttpError.UNSUPPORTED_SCHEME -> R.string.http_status_scheme
            HttpError.CLEARTEXT_BLOCKED -> R.string.http_status_cleartext
            HttpError.BUSY -> R.string.http_status_busy
            HttpError.TIMEOUT -> R.string.http_status_timeout
            HttpError.DNS -> R.string.http_status_dns
            HttpError.TLS -> R.string.http_status_tls_error
            HttpError.CONNECT -> R.string.http_status_connect
            HttpError.TOO_MANY_REDIRECTS -> R.string.http_status_redirects_limit
            HttpError.REDIRECT_LOOP -> R.string.http_status_redirect_loop
            HttpError.RESPONSE -> R.string.http_status_response
            HttpError.UNSUPPORTED -> R.string.http_status_unsupported
            HttpError.FAILED -> R.string.http_status_error
        }), color = MaterialTheme.colorScheme.error) }
        if (result != null) {
            Text("${result.status}${result.statusText?.let { " $it" } ?: ""}", style = MaterialTheme.typography.headlineMedium,
                color = statusColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
            if (result.bodyNotRead) Text(stringResource(R.string.http_status_body_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SystemReportCard(stringResource(R.string.http_status_result), rows)
            if (redirectRows.isNotEmpty()) SystemReportCard(redirectsLabel, redirectRows)
            if (headerRows.isEmpty()) Text(stringResource(R.string.http_status_no_headers), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else SystemReportCard(headersLabel, headerRows)
            SystemReportActions(report, false, model::run, showRefresh = false)
        }
    }
}
