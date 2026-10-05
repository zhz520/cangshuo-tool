package com.cangshuo.toolbox.feature.feedback.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.feature.feedback.domain.FeedbackFailure

@Composable
fun FeedbackRoute(factory: ViewModelProvider.Factory) {
    val model: FeedbackViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    if (state.user == null) return
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.feedback_title)) }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("BUG" to R.string.feedback_bug, "SUGGESTION" to R.string.feedback_suggestion, "OTHER" to R.string.feedback_other).forEach { (value, label) ->
                        FilterChip(selected = state.type == value, onClick = { model.type(value) }, enabled = !state.busy, label = { Text(stringResource(label)) })
                    }
                }
                OutlinedTextField(state.content, model::content, enabled = !state.busy, label = { Text(stringResource(R.string.feedback_content)) }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    supportingText = { Text("${state.content.length}/2000") })
                OutlinedTextField(state.contact, model::contact, enabled = !state.busy, label = { Text(stringResource(R.string.feedback_contact)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(stringResource(R.string.feedback_privacy), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = model::submit, enabled = !state.busy && state.content.isNotBlank()) { Text(stringResource(R.string.feedback_send)) }
                    TextButton(onClick = model::refresh, enabled = !state.busy) { Text(stringResource(R.string.feedback_refresh)) }
                    if (state.busy) ToolboxLoadingIndicator(compact = true)
                }
                state.failure?.let { error -> Text(stringResource(when (error) {
                    FeedbackFailure.INPUT -> R.string.feedback_input_error; FeedbackFailure.LOGIN -> R.string.auth_expired
                    FeedbackFailure.NETWORK -> R.string.auth_network_error; FeedbackFailure.LIMITED -> R.string.feedback_limited
                    FeedbackFailure.SERVICE -> R.string.auth_service_error; FeedbackFailure.UNCERTAIN -> R.string.feedback_uncertain
                }), color = MaterialTheme.colorScheme.error) }
                if (state.sent) Text(stringResource(R.string.feedback_sent), color = MaterialTheme.colorScheme.primary)
                if (state.ready && state.items.isEmpty()) Text(stringResource(R.string.feedback_empty))
                state.items.forEach { item ->
                    HorizontalDivider()
                    Text(stringResource(when (item.status) { "PENDING" -> R.string.feedback_pending; "PROCESSING" -> R.string.feedback_processing; else -> R.string.feedback_resolved }), style = MaterialTheme.typography.labelLarge)
                    Text(item.content); Text(item.createdAt, style = MaterialTheme.typography.bodySmall)
                    item.reply?.let { reply -> Text(stringResource(R.string.feedback_reply), style = MaterialTheme.typography.titleSmall); Text(reply) }
                }
            }
        }
    }
}
