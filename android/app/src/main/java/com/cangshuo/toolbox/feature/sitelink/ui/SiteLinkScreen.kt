package com.cangshuo.toolbox.feature.sitelink.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.sitelink.data.SiteLinkDto

/** Approved navigation links with an authenticated submission form. */
@Composable
fun SiteLinkScreen(
    state: SiteLinkUiState,
    signedIn: Boolean,
    onRefresh: () -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onDescription: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearFlags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showForm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (state.links.isEmpty() && !state.loading && !state.failed) onRefresh() }
    LaunchedEffect(state.submitted) { if (state.submitted) showForm = false }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (signedIn) stringResource(R.string.sitelink_submit_hint) else stringResource(R.string.sitelink_login_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (signedIn) ToolboxButton(onClick = { onClearFlags(); showForm = true }) {
                Text(stringResource(R.string.sitelink_submit))
            }
        }
        when {
            state.loading && state.links.isEmpty() -> ToolboxLoadingState(stringResource(R.string.sitelink_loading))
            state.failed && state.links.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.sitelink_failed), color = MaterialTheme.colorScheme.error)
                ToolboxOutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.action_retry)) }
            }
            state.links.isEmpty() -> Text(
                stringResource(R.string.sitelink_empty),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(state.links, key = { it.id }) { link ->
                    SiteLinkRowItem(link) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
                    }
                    ToolboxDivider(startIndent = 0.dp)
                }
            }
        }
    }
    if (showForm) AlertDialog(
        onDismissRequest = { if (!state.submitting) showForm = false },
        title = { Text(stringResource(R.string.sitelink_submit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolboxTextField(state.title, onTitle, label = stringResource(R.string.sitelink_form_title),
                    enabled = !state.submitting, singleLine = true, modifier = Modifier.fillMaxWidth())
                ToolboxTextField(state.url, onUrl, label = stringResource(R.string.sitelink_form_url),
                    enabled = !state.submitting, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                ToolboxTextField(state.description, onDescription, label = stringResource(R.string.sitelink_form_description),
                    enabled = !state.submitting, minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth())
                if (state.submitFailed) Text(stringResource(R.string.sitelink_submit_failed),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            ToolboxTextButton(onClick = onSubmit,
                enabled = !state.submitting && state.title.isNotBlank() && state.url.isNotBlank()) {
                if (state.submitting) ToolboxLoadingIndicator(compact = true)
                Text(stringResource(R.string.sitelink_submit))
            }
        },
        dismissButton = {
            ToolboxTextButton(onClick = { showForm = false }, enabled = !state.submitting) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
    if (state.submitted) AlertDialog(
        onDismissRequest = onClearFlags,
        title = { Text(stringResource(R.string.sitelink_submit)) },
        text = { Text(stringResource(R.string.sitelink_submitted)) },
        confirmButton = { ToolboxTextButton(onClick = onClearFlags) { Text(stringResource(R.string.action_understood)) } },
    )
}

@Composable
private fun SiteLinkRowItem(link: SiteLinkDto, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(link.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(link.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (link.description.isNotBlank()) Text(link.description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
