package com.cangshuo.toolbox.feature.coinflip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.coinflip.domain.*
import com.cangshuo.toolbox.ui.theme.ToolboxDimens

@Composable
fun CoinFlipRoute(factory: ViewModelProvider.Factory) {
    val model: CoinFlipViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) { model.start(); onStopOrDispose { model.stop() } }
    val heads = stringResource(R.string.coin_heads)
    val tails = stringResource(R.string.coin_tails)
    val unknown = stringResource(R.string.coin_no_value)
    val locale = LocalConfiguration.current.locales[0]
    val session = state.session
    val batch = state.lastBatch
    val display = state.pending ?: batch
    fun sideText(side: CoinSide) = if (side == CoinSide.HEADS) heads else tails
    fun ratio(count: Long) = if (session.total == 0L) unknown else String.format(locale, "%.1f%%", count * 100.0 / session.total)
    val rows = listOf(
        stringResource(R.string.coin_total) to session.total.toString(),
        heads to pluralStringResource(R.plurals.coin_count_ratio, session.heads.toInt(), session.heads, ratio(session.heads)),
        tails to pluralStringResource(R.plurals.coin_count_ratio, session.tails.toInt(), session.tails, ratio(session.tails)),
        stringResource(R.string.coin_streak) to (session.lastSide?.let {
            pluralStringResource(R.plurals.coin_streak_value, session.streak, sideText(it), session.streak)
        } ?: unknown),
        stringResource(R.string.coin_longest) to session.longestStreak.toString(),
    )
    val note = stringResource(R.string.coin_note)
    val historyNote = stringResource(R.string.coin_history_note, session.records.size, session.total)
    val batchSummary = batch?.let {
        stringResource(R.string.coin_batch_summary, it.sides.size, it.labels.heads, it.heads, it.labels.tails, it.tails)
    }
    val report = if (session.total == 0L || state.busy) null else if (state.csvReport) CoinPolicy.csv(session.records)
        else rows.joinToString("\n") { "${it.first}: ${it.second}" } + "\n${batchSummary.orEmpty()}\n$historyNote\n\n$note"
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val ready = stringResource(R.string.coin_ready)
    val flipping = stringResource(R.string.coin_flipping)
    val result = batch?.let { stringResource(R.string.coin_result, it.labels.forSide(it.sides.last())) }
    val visibleResult = if (state.busy) flipping else result ?: ready

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(ToolboxDimens.pageGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                val face = stringResource(R.string.coin_ready_face)
                ToolboxFlipCoin(display?.labels?.heads ?: face, display?.labels?.tails ?: face,
                    display?.sides?.last() == CoinSide.TAILS, state.pending != null, state.flipId, state.animation,
                    visibleResult, !state.busy, { model.flip(false, heads, tails) }, model::settle)
            }
            Text(visibleResult, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleLarge)
            if (state.busy && state.pending == null) ToolboxLoadingState(flipping)
            state.error?.let { error ->
                Text(stringResource(when (error) {
                    CoinError.LABELS -> R.string.coin_error_labels
                    CoinError.COUNT -> R.string.coin_error_count
                    CoinError.LIMIT -> R.string.coin_error_limit
                    CoinError.FAILED -> R.string.coin_error_failed
                    CoinError.INTERRUPTED -> R.string.coin_error_interrupted
                }), color = MaterialTheme.colorScheme.error)
            }
            ToolboxButton(onClick = { model.flip(false, heads, tails) }, enabled = !state.busy) { Text(stringResource(R.string.coin_flip_one)) }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.coin_batch_title))
            ToolboxTextField(state.countInput, model::updateCount, Modifier.fillMaxWidth(),
                label = stringResource(R.string.coin_batch_count), enabled = !state.busy, isError = state.error == CoinError.COUNT,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(3, 10, 50, 100).forEach { count ->
                    CoinOption(pluralStringResource(R.plurals.coin_preset, count, count), state.countInput == count.toString(), !state.busy) { model.updateCount(count.toString()) }
                }
                ToolboxOutlinedButton(onClick = { model.flip(true, heads, tails) }, enabled = !state.busy) { Text(stringResource(R.string.coin_flip_batch)) }
            }
            batchSummary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            batch?.takeIf { it.sides.size > 1 }?.let {
                Text(it.majority?.let { majority -> stringResource(R.string.coin_majority, it.labels.forSide(majority)) }
                    ?: stringResource(R.string.coin_tie), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.coin_labels_title))
            ToolboxTextField(state.headsInput, model::updateHeads, Modifier.fillMaxWidth(), label = stringResource(R.string.coin_heads_label),
                placeholder = heads, enabled = !state.busy, isError = state.error == CoinError.LABELS)
            ToolboxTextField(state.tailsInput, model::updateTails, Modifier.fillMaxWidth(), label = stringResource(R.string.coin_tails_label),
                placeholder = tails, enabled = !state.busy, isError = state.error == CoinError.LABELS)
            Text(stringResource(R.string.coin_labels_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().toggleable(state.animation, enabled = !state.busy, role = Role.Switch, onValueChange = model::setAnimation)
                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.coin_animation), Modifier.weight(1f))
                ToolboxSwitch(state.animation, null, enabled = !state.busy)
            }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.coin_stats_title))
            rows.forEach { (label, value) -> ToolResultRow(label, value) }
            ToolboxTextButton(onClick = { confirmClear = true }, enabled = session.total > 0 && !state.busy) { Text(stringResource(R.string.coin_clear)) }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.coin_history_title))
            if (session.records.isEmpty()) Text(stringResource(R.string.coin_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Text(historyNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                session.records.asReversed().take(if (state.showAll) CoinPolicy.HISTORY_LIMIT else 20).forEach { row ->
                    ToolResultRow(stringResource(R.string.coin_record_number, row.number),
                        stringResource(R.string.coin_record_value, sideText(row.side), row.labels.forSide(row.side)))
                }
                if (session.records.size > 20) ToolboxTextButton(onClick = { model.showAll(!state.showAll) }) {
                    Text(stringResource(if (state.showAll) R.string.coin_history_less else R.string.coin_history_all))
                }
            }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.coin_report))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoinOption(stringResource(R.string.coin_summary), !state.csvReport) { model.selectReport(false) }
                CoinOption(stringResource(R.string.coin_csv), state.csvReport) { model.selectReport(true) }
            }
            SystemReportActions(report, state.busy, {}, showRefresh = false)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ToolboxDimens.pageBottom))
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.coin_clear)) },
        text = { Text(stringResource(R.string.coin_clear_message)) }, confirmButton = {
            ToolboxTextButton(onClick = { model.clear(); confirmClear = false }) { Text(stringResource(R.string.coin_clear_confirm)) }
        }, dismissButton = { ToolboxTextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.coin_cancel)) } })
}

@Composable
private fun CoinOption(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val modifier = Modifier.semantics { this.selected = selected }
    if (selected) ToolboxButton(onClick, modifier, enabled) { Text(label) }
    else ToolboxOutlinedButton(onClick, modifier, enabled) { Text(label) }
}
