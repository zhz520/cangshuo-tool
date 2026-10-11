package com.cangshuo.toolbox.feature.feedback.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.feedback.domain.FeedbackFailure

@Composable
fun FeedbackRoute(factory: ViewModelProvider.Factory, onLogin: (() -> Unit)? = null) {
    val model: FeedbackViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(true) }
    if (state.user == null) {
        Text(stringResource(R.string.feedback_login_hint),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(onLogin != null) ToolboxButton(onClick=onLogin) {Text(stringResource(R.string.auth_login))}
        return
    }
    ToolGroupCard(modifier = Modifier.fillMaxWidth()) {
        ToolboxTextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.feedback_title)) }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("BUG" to R.string.feedback_bug, "SUGGESTION" to R.string.feedback_suggestion, "OTHER" to R.string.feedback_other).forEach { (value, label) ->
                    FilterChip(selected = state.type == value, onClick = { model.type(value) }, enabled = !state.busy, label = { Text(stringResource(label)) })
                }
            }
            ToolboxTextField(state.content, model::content, enabled = !state.busy, label = stringResource(R.string.feedback_content), modifier = Modifier.fillMaxWidth(), minLines = 3,
                supportingText = { Text("${state.content.length}/2000") })
            ToolboxTextField(state.contact, model::contact, enabled = !state.busy, label = stringResource(R.string.feedback_contact), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text(stringResource(R.string.feedback_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolboxButton(onClick = model::submit, enabled = !state.busy && state.content.isNotBlank()) { Text(stringResource(R.string.feedback_send)) }
                ToolboxTextButton(onClick = model::refresh, enabled = !state.busy) { Text(stringResource(R.string.feedback_refresh)) }
                if (state.busy) ToolboxLoadingIndicator(compact = true)
            }
            state.failure?.let { error -> Text(stringResource(when (error) {
                FeedbackFailure.INPUT -> R.string.feedback_input_error; FeedbackFailure.LOGIN -> R.string.auth_expired
                FeedbackFailure.NETWORK -> R.string.auth_network_error; FeedbackFailure.LIMITED -> R.string.feedback_limited
                FeedbackFailure.SERVICE -> R.string.auth_service_error; FeedbackFailure.UNCERTAIN -> R.string.feedback_uncertain
            }), color = MaterialTheme.colorScheme.error) }
            if (state.sent) Text(stringResource(R.string.feedback_sent), color = MaterialTheme.colorScheme.primary)
            if (state.ready && state.items.isEmpty()) Text(stringResource(R.string.feedback_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.items.forEach { item ->
                ToolboxDivider()
                Text(stringResource(when (item.status) { "PENDING" -> R.string.feedback_pending; "PROCESSING" -> R.string.feedback_processing; else -> R.string.feedback_resolved }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.content)
                Text(item.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.reply?.let { reply -> Text(stringResource(R.string.feedback_reply), style = MaterialTheme.typography.titleSmall); Text(reply) }
            }
        }
    }
}
