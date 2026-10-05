package com.cangshuo.toolbox.feature.home.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.feature.home.domain.HomeAnnouncement
import com.cangshuo.toolbox.feature.home.domain.HomeRecommendation

@Composable
fun HomeExtrasRoute(factory: ViewModelProvider.Factory, onTool: (String) -> Unit) {
    val model: HomeExtrasViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    var announcement by remember { mutableStateOf<HomeAnnouncement?>(null) }
    var external by remember { mutableStateOf<HomeRecommendation?>(null) }
    var linkFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LifecycleStartEffect(Unit) { model.refresh(); onStopOrDispose { } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.busy) ToolboxLoadingIndicator(compact = true)
        if (state.failed) Row {
            Text(stringResource(R.string.home_updates_failed), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { model.refresh(true) }) { Text(stringResource(R.string.action_retry)) }
        }
        if (state.announcements.isNotEmpty()) {
            Text(stringResource(R.string.home_announcements), style = MaterialTheme.typography.titleMedium)
            state.announcements.forEach { item ->
                OutlinedButton(onClick = { announcement = item }, modifier = Modifier.fillMaxWidth()) {
                    Text(item.title, color = if (item.level == "INFO") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        }
        if (state.recommendations.isNotEmpty()) {
            Text(stringResource(R.string.home_recommendation_slots), style = MaterialTheme.typography.titleMedium)
            state.recommendations.forEach { item ->
                Card(onClick = { item.toolCode?.let(onTool) ?: run { external = item } }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        item.subtitle?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (item.linkUrl != null) Text(stringResource(R.string.home_external_link), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    announcement?.let { item -> AlertDialog(onDismissRequest = { announcement = null }, title = { Text(item.title) },
        text = { Text(item.body, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { announcement = null }) { Text(stringResource(R.string.action_understood)) } }) }
    external?.let { item -> AlertDialog(onDismissRequest = { external = null }, title = { Text(stringResource(R.string.home_external_link)) },
        text = { Text(item.linkUrl.orEmpty()) }, confirmButton = { TextButton(onClick = {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, item.linkUrl!!.toUri())); external = null }
            catch (_: RuntimeException) { external = null; linkFailed = true }
        }) { Text(stringResource(R.string.home_open_link)) } },
        dismissButton = { TextButton(onClick = { external = null }) { Text(stringResource(R.string.action_cancel)) } }) }
    if (linkFailed) AlertDialog(onDismissRequest = { linkFailed = false }, text = { Text(stringResource(R.string.home_link_failed)) },
        confirmButton = { TextButton(onClick = { linkFailed = false }) { Text(stringResource(R.string.action_understood)) } })
}
