package com.cangshuo.toolbox.feature.answerbook.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.answerbook.domain.*
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun AnswerBookRoute(factory: ViewModelProvider.Factory) {
    val model: AnswerBookViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val texts = stringArrayResource(R.array.answerbook_texts)
    val current = state.current
    var clear by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val reveal = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val answer = current?.let { texts[it] }
    val report = answer?.let { stringResource(R.string.answerbook_report, it, themeName(AnswerBookPolicy.entries[current].theme)) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(ToolboxDimens.pageGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.answerbook_instruction), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnswerTheme.entries.forEach { theme ->
                    val label = themeName(theme)
                    if (theme == state.theme) ToolboxButton({ model.theme(theme) }, enabled = !state.busy) { Text(label) }
                    else ToolboxOutlinedButton({ model.theme(theme) }, enabled = !state.busy) { Text(label) }
                }
            }
            Text(stringResource(R.string.answerbook_pool, AnswerBookPolicy.pool(state.theme).size,
                AnswerBookPolicy.remaining(state.book, state.theme)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().heightIn(min = 220.dp).bringIntoViewRequester(reveal).clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer).padding(24.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(targetState = current, transitionSpec = { ToolboxMotion.tab() }, label = "answer") { id ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(if (id == null) stringResource(R.string.answerbook_cover) else texts[id],
                            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        if (id != null) Text(stringResource(R.string.answerbook_number, id + 1),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolboxButton(model::draw, enabled = state.loaded && !state.busy && state.error == null) {
                    Text(stringResource(if (current == null) R.string.answerbook_open else R.string.answerbook_again))
                }
                if (current != null) ToolboxOutlinedButton({ model.favorite(current) }, enabled = !state.busy && state.error == null) {
                    Text(stringResource(if (current in state.book.favorites) R.string.answerbook_unfavorite else R.string.answerbook_favorite))
                }
            }
            if (state.busy) ToolboxLoadingIndicator(compact = true)
            state.error?.let {
                Text(stringResource(if (it == BookError.LOAD) R.string.answerbook_load_error else R.string.answerbook_write_error), color = MaterialTheme.colorScheme.error)
                ToolboxOutlinedButton(model::load, enabled = !state.busy) { Text(stringResource(R.string.answerbook_reload)) }
                if (it == BookError.LOAD) ToolboxTextButton({ reset = true }, enabled = !state.busy) { Text(stringResource(R.string.answerbook_reset)) }
            }
            SystemReportActions(report, state.busy, {}, showRefresh = false)
            Text(stringResource(R.string.answerbook_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToolboxDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.favoritesOnly) ToolboxOutlinedButton({ model.tab(false) }) { Text(stringResource(R.string.answerbook_history)) }
                else ToolboxButton({ model.tab(false) }) { Text(stringResource(R.string.answerbook_history)) }
                if (state.favoritesOnly) ToolboxButton({ model.tab(true) }) { Text(stringResource(R.string.answerbook_favorites, state.book.favorites.size)) }
                else ToolboxOutlinedButton({ model.tab(true) }) { Text(stringResource(R.string.answerbook_favorites, state.book.favorites.size)) }
            }
            if (state.favoritesOnly) {
                if (state.book.favorites.isEmpty()) Text(stringResource(R.string.answerbook_empty_favorites), color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.book.favorites.sorted().forEach { id ->
                    ToolListItem(title = texts[id], subtitle = themeName(AnswerBookPolicy.entries[id].theme), icon = R.drawable.ic_star,
                        onClick = { model.select(id); scope.launch { reveal.bringIntoView() } }, subtitleMaxLines = Int.MAX_VALUE)
                }
            } else {
                Text(stringResource(R.string.answerbook_history_note, AnswerBookPolicy.HISTORY_LIMIT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.book.history.isEmpty()) Text(stringResource(R.string.answerbook_empty_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.book.history.forEach { draw ->
                    val date = remember(draw.at) { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(draw.at)) }
                    ToolListItem(title = texts[draw.answer], subtitle = "$date · ${themeName(draw.theme)}", onClick = { model.select(draw.answer); scope.launch { reveal.bringIntoView() } },
                        icon = if (draw.answer in state.book.favorites) R.drawable.ic_star else null)
                }
                if (state.book.history.isNotEmpty()) ToolboxTextButton({ clear = true }, enabled = !state.busy && state.error == null) { Text(stringResource(R.string.answerbook_clear)) }
            }
            Spacer(Modifier.height(ToolboxDimens.pageBottom))
        }
    }
    if (clear || reset) AlertDialog(onDismissRequest = { clear = false; reset = false },
        title = { Text(stringResource(if (reset) R.string.answerbook_reset else R.string.answerbook_clear)) },
        text = { Text(stringResource(if (reset) R.string.answerbook_reset_confirm else R.string.answerbook_clear_confirm)) },
        confirmButton = { ToolboxTextButton({ if (reset) model.reset() else model.clearHistory(); clear = false; reset = false }) { Text(stringResource(R.string.answerbook_confirm)) } },
        dismissButton = { ToolboxTextButton({ clear = false; reset = false }) { Text(stringResource(R.string.answerbook_cancel)) } })
}

@Composable
private fun themeName(theme: AnswerTheme): String = stringResource(when (theme) {
    AnswerTheme.ALL -> R.string.answerbook_all; AnswerTheme.ACTION -> R.string.answerbook_action
    AnswerTheme.REST -> R.string.answerbook_rest; AnswerTheme.PERSPECTIVE -> R.string.answerbook_perspective
})
