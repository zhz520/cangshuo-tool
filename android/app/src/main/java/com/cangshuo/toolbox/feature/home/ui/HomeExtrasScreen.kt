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
import com.cangshuo.toolbox.core.ui.ToolListItem
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxChevron
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
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
            ToolboxTextButton(onClick = { model.refresh(true) }) { Text(stringResource(R.string.action_retry)) }
        }
        if (state.announcements.isNotEmpty()) {
            ToolSectionHeader(stringResource(R.string.home_announcements))
            state.announcements.forEach { item ->
                ToolListItem(item.title, icon=R.drawable.ic_info,onClick={announcement=item},trailing={ToolboxChevron()})
            }
        }
        if (state.recommendations.isNotEmpty()) {
            ToolSectionHeader(stringResource(R.string.home_recommendation_slots))
            state.recommendations.forEach { item ->
                ToolListItem(item.title,subtitle=item.subtitle,icon=R.drawable.ic_tools,
                    onClick={item.toolCode?.let(onTool) ?: run {external=item}},trailing={ToolboxChevron()})
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
