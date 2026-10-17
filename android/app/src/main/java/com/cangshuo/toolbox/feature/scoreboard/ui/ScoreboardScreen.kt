package com.cangshuo.toolbox.feature.scoreboard.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.SystemReportActions
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolListItem
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxBadge
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreAction
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreActionKind
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardCore
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreboardPolicy
import com.cangshuo.toolbox.feature.scoreboard.domain.ScoreSide
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ScoreboardScreen(
    state: ScoreboardUiState,
    onIncrement: (ScoreSide) -> Unit,
    onDecrement: (ScoreSide) -> Unit,
    onSetScore: (ScoreSide, Int) -> Unit,
    onEditNames: (String, String) -> Unit,
    onUndo: () -> Unit,
    onSwap: () -> Unit,
    onNextPeriod: () -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onReset: () -> Unit,
    onStep: (Int) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onShowAllHistory: (Boolean) -> Unit,
) {
    var showNames by rememberSaveable { mutableStateOf(false) }
    var scoreSide by rememberSaveable { mutableStateOf<ScoreSide?>(null) }
    var confirmNextPeriod by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmResetTimer by rememberSaveable { mutableStateOf(false) }
    val core = state.session.core
    val leftName = teamName(core, ScoreSide.LEFT)
    val rightName = teamName(core, ScoreSide.RIGHT)
    val timerStatus = when {
        state.session.running -> stringResource(R.string.scoreboard_timer_running)
        state.displayMs > 0L -> stringResource(R.string.scoreboard_timer_paused)
        else -> stringResource(R.string.scoreboard_timer_ready)
    }
    val note = stringResource(R.string.scoreboard_note)
    val context = LocalContext.current
    val report = buildScoreboardReport(
        context = context,
        core = core,
        leftName = leftName,
        rightName = rightName,
        timer = ScoreboardPolicy.formatDuration(state.displayMs),
        timerStatus = timerStatus,
        step = state.session.step,
        note = note,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ToolboxDimens.pageGutter,
            top = ToolboxDimens.pageTop,
            end = ToolboxDimens.pageGutter,
            bottom = ToolboxDimens.pageBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "board") {
            ToolSectionHeader(
                title = stringResource(R.string.scoreboard_board_title),
                trailing = {
                    ToolboxTextButton(onClick = { showNames = true }) {
                        Text(stringResource(R.string.scoreboard_edit_names))
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            ScoreBoard(
                core = core,
                step = state.session.step,
                onIncrement = onIncrement,
                onDecrement = onDecrement,
                onSetScore = { scoreSide = it },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolboxBadge(
                    stringResource(R.string.scoreboard_period_value, core.period),
                    emphasized = true,
                )
                Text(
                    text = stringResource(R.string.scoreboard_tap_score_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "timer") {
            ToolGroupCard(title = stringResource(R.string.scoreboard_timer_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = ScoreboardPolicy.formatDuration(state.displayMs),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                    Text(
                        text = timerStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ToolboxButton(onClick = onToggleTimer) {
                        Text(stringResource(if (state.session.running) R.string.scoreboard_timer_pause
                        else if (state.displayMs > 0L) R.string.scoreboard_timer_resume
                        else R.string.scoreboard_timer_start))
                    }
                    ToolboxOutlinedButton(
                        onClick = { if (state.displayMs > 0L) confirmResetTimer = true else onResetTimer() },
                        enabled = state.displayMs > 0L,
                    ) { Text(stringResource(R.string.scoreboard_timer_reset)) }
                    ToolboxOutlinedButton(
                        onClick = onSwap,
                        enabled = core.leftScore != 0 || core.rightScore != 0 ||
                            core.leftName.isNotBlank() || core.rightName.isNotBlank(),
                    ) { Text(stringResource(R.string.scoreboard_swap)) }
                    ToolboxOutlinedButton(
                        onClick = { confirmNextPeriod = true },
                        enabled = core.period < ScoreboardPolicy.MAX_PERIOD,
                    ) {
                        Text(stringResource(R.string.scoreboard_next_period))
                    }
                    ToolboxTextButton(onClick = onUndo, enabled = state.session.journal.isNotEmpty()) {
                        Text(stringResource(R.string.scoreboard_undo))
                    }
                }
                ToolboxDivider(startIndent = 0.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = state.keepScreenOn,
                        role = Role.Switch,
                        onValueChange = onKeepScreenOn,
                    ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.scoreboard_keep_screen), Modifier.weight(1f))
                    ToolboxSwitch(state.keepScreenOn, null)
                }
            }
        }
        item(key = "step") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolSectionHeader(stringResource(R.string.scoreboard_step_title))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (ScoreboardPolicy.MIN_STEP..ScoreboardPolicy.MAX_STEP).forEach { step ->
                        ScoreboardOption(
                            label = stringResource(R.string.scoreboard_step_value, step),
                            selected = state.session.step == step,
                            modifier = Modifier.weight(1f),
                            onClick = { onStep(step) },
                        )
                    }
                }
            }
        }
        item(key = "history") {
            ToolSectionHeader(stringResource(R.string.scoreboard_history_title))
            if (state.session.journal.isEmpty()) {
                Text(
                    stringResource(R.string.scoreboard_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(
                        R.string.scoreboard_history_count,
                        state.session.journal.size,
                        ScoreboardPolicy.MAX_HISTORY,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val visible = state.session.journal.asReversed().let {
                    if (state.showAllHistory) it else it.take(ScoreboardPolicy.DISPLAY_HISTORY)
                }
                visible.forEachIndexed { index, entry ->
                    ToolListItem(
                        title = actionLabel(entry.action, entry.after),
                        subtitle = stringResource(
                            R.string.scoreboard_history_time,
                            entry.after.period,
                            formatEventTime(entry.atEpochMs),
                        ),
                        trailing = {
                            Text(
                                text = stringResource(
                                    R.string.scoreboard_history_score,
                                    entry.after.leftScore,
                                    entry.after.rightScore,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        },
                        showDivider = index != visible.lastIndex,
                        dividerIndent = 0.dp,
                    )
                }
                if (state.session.journal.size > ScoreboardPolicy.DISPLAY_HISTORY) {
                    ToolboxTextButton(onClick = { onShowAllHistory(!state.showAllHistory) }) {
                        Text(stringResource(if (state.showAllHistory) R.string.scoreboard_show_less
                        else R.string.scoreboard_show_all))
                    }
                }
            }
        }
        item(key = "report") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolSectionHeader(stringResource(R.string.scoreboard_report))
                SystemReportActions(report, loading = false, onRefresh = {}, showRefresh = false)
            }
        }
        item(key = "reset") {
            ToolboxTextButton(onClick = { confirmReset = true }) {
                Text(stringResource(R.string.scoreboard_reset), color = MaterialTheme.colorScheme.error)
            }
        }
        item(key = "note") {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "bottom") { Spacer(Modifier.height(ToolboxDimens.pageBottom)) }
    }

    if (showNames) {
        NameDialog(
            initialLeft = leftName,
            initialRight = rightName,
            onDismiss = { showNames = false },
            onSave = { left, right ->
                onEditNames(left, right)
                showNames = false
            },
        )
    }
    scoreSide?.let { side ->
        ScoreDialog(
            name = if (side == ScoreSide.LEFT) leftName else rightName,
            initial = core.score(side).toString(),
            onDismiss = { scoreSide = null },
            onSave = { score ->
                onSetScore(side, score)
                scoreSide = null
            },
        )
    }
    if (confirmNextPeriod) {
        ConfirmDialog(
            title = stringResource(R.string.scoreboard_next_period_title),
            message = stringResource(R.string.scoreboard_next_period_message),
            confirm = stringResource(R.string.scoreboard_next_period_confirm),
            onDismiss = { confirmNextPeriod = false },
            onConfirm = {
                onNextPeriod()
                confirmNextPeriod = false
            },
        )
    }
    if (confirmResetTimer) {
        ConfirmDialog(
            title = stringResource(R.string.scoreboard_timer_reset_title),
            message = stringResource(R.string.scoreboard_timer_reset_message),
            confirm = stringResource(R.string.scoreboard_timer_reset),
            onDismiss = { confirmResetTimer = false },
            onConfirm = {
                onResetTimer()
                confirmResetTimer = false
            },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.scoreboard_reset_title),
            message = stringResource(R.string.scoreboard_reset_message),
            confirm = stringResource(R.string.scoreboard_reset_confirm),
            destructive = true,
            onDismiss = { confirmReset = false },
            onConfirm = {
                onReset()
                confirmReset = false
            },
        )
    }
}

@Composable
private fun ScoreBoard(
    core: ScoreboardCore,
    step: Int,
    onIncrement: (ScoreSide) -> Unit,
    onDecrement: (ScoreSide) -> Unit,
    onSetScore: (ScoreSide) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            TeamPanel(
                modifier = Modifier.weight(1f),
                side = ScoreSide.LEFT,
                name = teamName(core, ScoreSide.LEFT),
                score = core.leftScore,
                step = step,
                onIncrement = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onIncrement(ScoreSide.LEFT) },
                onDecrement = { onDecrement(ScoreSide.LEFT) },
                onSetScore = { onSetScore(ScoreSide.LEFT) },
            )
            Box(
                Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant),
            )
            TeamPanel(
                modifier = Modifier.weight(1f),
                side = ScoreSide.RIGHT,
                name = teamName(core, ScoreSide.RIGHT),
                score = core.rightScore,
                step = step,
                onIncrement = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onIncrement(ScoreSide.RIGHT) },
                onDecrement = { onDecrement(ScoreSide.RIGHT) },
                onSetScore = { onSetScore(ScoreSide.RIGHT) },
            )
        }
    }
}

@Composable
private fun TeamPanel(
    modifier: Modifier,
    side: ScoreSide,
    name: String,
    score: Int,
    step: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetScore: () -> Unit,
) {
    val scoreDescription = stringResource(R.string.scoreboard_score_accessibility, name, score)
    val decreaseDescription = stringResource(R.string.scoreboard_decrease_score_accessibility, name, step)
    val increaseDescription = stringResource(R.string.scoreboard_increase_score_accessibility, name, step)
    Column(
        modifier = modifier.padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = name,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .semantics(mergeDescendants = true) {
                    contentDescription = scoreDescription
                    liveRegion = LiveRegionMode.Polite
                    role = Role.Button
                }
                .clickable(role = Role.Button, onClick = onSetScore)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            AnimatedContent(
                targetState = score,
                transitionSpec = {
                    (fadeIn(tween(130)) + scaleIn(tween(130), initialScale = 0.9f))
                        .togetherWith(fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.94f))
                },
                label = "score",
            ) { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolboxOutlinedButton(
                onClick = onDecrement,
                modifier = Modifier.weight(1f).semantics { contentDescription = decreaseDescription },
                enabled = score > 0,
            ) { Text("−$step") }
            ToolboxButton(
                onClick = onIncrement,
                modifier = Modifier.weight(1f).semantics { contentDescription = increaseDescription },
            ) { Text("+$step") }
        }
    }
}

@Composable
private fun ScoreboardOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val selectedModifier = modifier.semantics { this.selected = selected }
    if (selected) {
        ToolboxButton(onClick, selectedModifier) { Text(label) }
    } else {
        ToolboxOutlinedButton(onClick, selectedModifier) { Text(label) }
    }
}

@Composable
private fun NameDialog(
    initialLeft: String,
    initialRight: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var left by rememberSaveable { mutableStateOf(initialLeft) }
    var right by rememberSaveable { mutableStateOf(initialRight) }
    val leftValid = ScoreboardPolicy.isUsableName(left)
    val rightValid = ScoreboardPolicy.isUsableName(right)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scoreboard_edit_names_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolboxTextField(
                    value = left,
                    onValueChange = { left = ScoreboardPolicy.nameInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.scoreboard_left_name),
                    isError = !leftValid,
                )
                ToolboxTextField(
                    value = right,
                    onValueChange = { right = ScoreboardPolicy.nameInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.scoreboard_right_name),
                    isError = !rightValid,
                )
                Text(
                    text = stringResource(R.string.scoreboard_name_hint, ScoreboardPolicy.MAX_NAME_CODE_POINTS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            ToolboxTextButton(
                onClick = { onSave(left.trim(), right.trim()) },
                enabled = leftValid && rightValid &&
                    (left.trim() != initialLeft || right.trim() != initialRight),
            ) { Text(stringResource(R.string.scoreboard_save)) }
        },
        dismissButton = {
            ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.scoreboard_cancel)) }
        },
    )
}

@Composable
private fun ScoreDialog(
    name: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val parsed = value.toIntOrNull()
    val valid = parsed != null && parsed in 0..ScoreboardPolicy.MAX_SCORE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scoreboard_score_dialog_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolboxTextField(
                    value = value,
                    onValueChange = { candidate ->
                        if (candidate.length <= 3 && candidate.all(Char::isDigit)) value = candidate
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.scoreboard_score_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = value.isNotEmpty() && !valid,
                )
                Text(
                    text = stringResource(R.string.scoreboard_score_range, ScoreboardPolicy.MAX_SCORE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            ToolboxTextButton(onClick = { parsed?.let(onSave) }, enabled = valid) {
                Text(stringResource(R.string.scoreboard_save))
            }
        },
        dismissButton = {
            ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.scoreboard_cancel)) }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            ToolboxTextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.scoreboard_cancel)) }
        },
    )
}

@Composable
private fun teamName(core: ScoreboardCore, side: ScoreSide): String {
    val stored = if (side == ScoreSide.LEFT) core.leftName else core.rightName
    return stored.ifBlank {
        stringResource(
            if (side == ScoreSide.LEFT) R.string.scoreboard_team_default_left
            else R.string.scoreboard_team_default_right,
        )
    }
}

@Composable
private fun actionLabel(action: ScoreAction, core: ScoreboardCore): String {
    val sideName = action.side?.let { teamName(core, it) }.orEmpty()
    return when (action.kind) {
        ScoreActionKind.SCORE -> stringResource(
            if (action.delta >= 0) R.string.scoreboard_action_score_increase
            else R.string.scoreboard_action_score_decrease,
            sideName,
            kotlin.math.abs(action.delta),
        )
        ScoreActionKind.SET_SCORE -> stringResource(
            R.string.scoreboard_action_set,
            sideName,
            action.targetScore ?: 0,
        )
        ScoreActionKind.SWAP -> stringResource(R.string.scoreboard_action_swap)
        ScoreActionKind.NEXT_PERIOD -> stringResource(R.string.scoreboard_action_next, core.period)
        ScoreActionKind.EDIT_NAMES -> stringResource(R.string.scoreboard_action_names)
    }
}

private fun formatEventTime(epochMs: Long): String = runCatching {
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}.getOrDefault("--:--:--")

private fun buildScoreboardReport(
    context: Context,
    core: ScoreboardCore,
    leftName: String,
    rightName: String,
    timer: String,
    timerStatus: String,
    step: Int,
    note: String,
): String = buildString {
    appendLine(context.getString(R.string.scoreboard_report_title))
    appendLine(context.getString(
        R.string.scoreboard_report_line,
        leftName,
        context.getString(R.string.scoreboard_report_team_score, core.leftScore),
    ))
    appendLine(context.getString(
        R.string.scoreboard_report_line,
        rightName,
        context.getString(R.string.scoreboard_report_team_score, core.rightScore),
    ))
    appendLine(context.getString(
        R.string.scoreboard_report_period,
        core.period,
    ))
    appendLine(context.getString(R.string.scoreboard_report_timer, timer, timerStatus))
    append(context.getString(
        R.string.scoreboard_report_step,
        context.getString(R.string.scoreboard_step_value, step),
    ))
    appendLine()
    append(note)
}
