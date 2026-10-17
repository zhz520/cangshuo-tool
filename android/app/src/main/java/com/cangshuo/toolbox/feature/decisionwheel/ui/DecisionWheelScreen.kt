package com.cangshuo.toolbox.feature.decisionwheel.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.SystemReportActions
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelError
import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelPolicy
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelOption
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelPreset
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelResult
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs
import kotlin.math.atan2

@Composable
fun DecisionWheelScreen(
    state: DecisionWheelUiState,
    onSpin: (Float) -> Unit,
    onSettled: (Long, Float) -> Unit,
    onRotationChanged: (Float) -> Unit,
    onPreset: (WheelPreset) -> Unit,
    onResetOptions: () -> Unit,
    onSaveOption: (Int?, String, Int) -> Boolean,
    onRemoveOption: (Int) -> Boolean,
    onAvoidRepeat: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onClearEditError: () -> Unit,
    onClearHistory: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val rotation = remember { Animatable(state.rotation) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    var interactionLocked by remember { mutableStateOf(false) }
    var dragJob by remember { mutableStateOf<Job?>(null) }
    val interactionLockedNow by rememberUpdatedState(interactionLocked)
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var showClearHistoryConfirm by rememberSaveable { mutableStateOf(false) }
    var showAllHistory by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.spin?.id) {
        val spin = state.spin ?: return@LaunchedEffect
        interactionLocked = true
        if (state.settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        try {
            try {
                rotation.animateTo(
                    targetValue = spin.targetRotation,
                    animationSpec = tween(
                        durationMillis = 2_700,
                        easing = CubicBezierEasing(0.22f, 0f, 0.18f, 1f),
                    ),
                )
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                rotation.snapTo(spin.targetRotation)
            }
            if (state.settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onSettled(spin.id, spin.targetRotation)
        } finally {
            interactionLocked = false
        }
    }

    val historyTime = remember(timeFormat) { { timeMillis: Long -> timeFormat.format(Date(timeMillis)) } }
    val reportTitle = stringResource(R.string.decision_wheel_history_report_title)
    val reportLine = stringResource(R.string.decision_wheel_history_report_line)
    val report = remember(state.history, timeFormat, reportTitle, reportLine) {
        if (state.history.isEmpty()) null else buildString {
            appendLine(reportTitle)
            state.history.take(DecisionWheelPolicy.MAX_HISTORY).forEachIndexed { index, item ->
                appendLine(
                    String.format(
                        reportLine,
                        index + 1,
                        item.label,
                        timeFormat.format(Date(item.timestampMillis)),
                    ),
                )
            }
        }.trimEnd()
    }

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
        item(key = "wheel") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolSectionHeader(stringResource(R.string.decision_wheel_wheel_title))
                WheelPanel(
                    state = state,
                    rotation = rotation.value,
                    onRotationDelta = { delta ->
                        if (!state.spinning && !interactionLocked) {
                            dragJob?.cancel()
                            dragJob = scope.launch {
                                if (!interactionLockedNow) rotation.snapTo(rotation.value + delta)
                            }
                        }
                    },
                    onDragFinished = { onRotationChanged(rotation.value) },
                    onSpin = {
                        if (!interactionLocked && !state.spinning) {
                            interactionLocked = true
                            scope.launch {
                                dragJob?.cancelAndJoin()
                                dragJob = null
                                onRotationChanged(rotation.value)
                                onSpin(rotation.value)
                            }
                        }
                    },
                )
            }
        }
        item(key = "result") {
            AnimatedVisibility(
                visible = state.resultVisible,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                state.lastResult?.let { result ->
                    ResultCard(
                        result = result,
                        time = historyTime(result.timestampMillis),
                        onDismiss = onDismissResult,
                    )
                }
            }
        }
        item(key = "presets") {
            ToolGroupCard(title = stringResource(R.string.decision_wheel_presets_title)) {
                Text(
                    stringResource(R.string.decision_wheel_presets_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetButton(R.string.decision_wheel_preset_meals, !state.spinning) { onPreset(WheelPreset.MEALS) }
                    PresetButton(R.string.decision_wheel_preset_yes_no, !state.spinning) { onPreset(WheelPreset.YES_NO) }
                    PresetButton(R.string.decision_wheel_preset_activities, !state.spinning) { onPreset(WheelPreset.ACTIVITIES) }
                }
            }
        }
        item(key = "options") {
            ToolGroupCard(title = stringResource(R.string.decision_wheel_options_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.decision_wheel_option_count,
                            state.settings.options.size,
                            DecisionWheelPolicy.MAX_OPTIONS,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.settings.options.isNotEmpty()) {
                        ToolboxTextButton(onClick = { showResetConfirm = true }, enabled = !state.spinning) {
                            Text(stringResource(R.string.decision_wheel_restore_defaults))
                        }
                    }
                }
                state.settings.options.forEachIndexed { index, option ->
                    if (index > 0) ToolboxDivider(startIndent = 0.dp)
                    OptionRow(
                        index = index,
                        option = option,
                        share = DecisionWheelPolicy.percent(option.weight, state.settings.options),
                        canDelete = state.settings.options.size > DecisionWheelPolicy.MIN_OPTIONS && !state.spinning,
                        onEdit = {
                            editingIndex = index
                            onClearEditError()
                            editorOpen = true
                        },
                        onDelete = { onRemoveOption(index) },
                    )
                }
                ToolboxOutlinedButton(
                    onClick = {
                        editingIndex = null
                        onClearEditError()
                        editorOpen = true
                    },
                    enabled = state.settings.options.size < DecisionWheelPolicy.MAX_OPTIONS && !state.spinning,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.decision_wheel_add_option)) }
            }
        }
        item(key = "settings") {
            ToolGroupCard(title = stringResource(R.string.decision_wheel_behavior_title)) {
                ToggleRow(
                    title = stringResource(R.string.decision_wheel_avoid_repeat),
                    subtitle = stringResource(R.string.decision_wheel_avoid_repeat_hint),
                    checked = state.settings.avoidRepeat,
                    enabled = !state.spinning,
                    onCheckedChange = onAvoidRepeat,
                )
                ToolboxDivider(startIndent = 0.dp)
                ToggleRow(
                    title = stringResource(R.string.decision_wheel_haptics),
                    subtitle = stringResource(R.string.decision_wheel_haptics_hint),
                    checked = state.settings.haptics,
                    onCheckedChange = onHaptics,
                )
            }
        }
        item(key = "history") {
            ToolGroupCard(title = stringResource(R.string.decision_wheel_history_title)) {
                if (state.history.isEmpty()) {
                    Text(
                        stringResource(R.string.decision_wheel_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val shown = if (showAllHistory) state.history else state.history.take(6)
                    shown.forEachIndexed { index, item ->
                        if (index > 0) ToolboxDivider(startIndent = 0.dp)
                        HistoryRow(index = index + 1, result = item, time = historyTime(item.timestampMillis))
                    }
                    if (state.history.size > 6) {
                        ToolboxTextButton(onClick = { showAllHistory = !showAllHistory }) {
                            Text(
                                stringResource(
                                    if (showAllHistory) R.string.decision_wheel_show_less
                                    else R.string.decision_wheel_show_all,
                                ),
                            )
                        }
                    }
                    ToolboxTextButton(onClick = { showClearHistoryConfirm = true }, enabled = !state.spinning) {
                        Text(stringResource(R.string.decision_wheel_clear_history))
                    }
                }
            }
        }
        item(key = "report") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolSectionHeader(stringResource(R.string.decision_wheel_report_title))
                SystemReportActions(report = report, loading = false, onRefresh = {}, showRefresh = false)
                Text(
                    stringResource(R.string.decision_wheel_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (editorOpen) {
        OptionEditorDialog(
            option = editingIndex?.let { state.settings.options.getOrNull(it) },
            error = state.editError,
            onDismiss = { editorOpen = false; onClearEditError() },
            onClearError = onClearEditError,
            onSave = { label, weight ->
                if (onSaveOption(editingIndex, label, weight)) editorOpen = false
            },
        )
    }
    if (showResetConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.decision_wheel_restore_title),
            message = stringResource(R.string.decision_wheel_restore_message),
            confirm = stringResource(R.string.decision_wheel_restore_confirm),
            onDismiss = { showResetConfirm = false },
            onConfirm = { showResetConfirm = false; onResetOptions() },
        )
    }
    if (showClearHistoryConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.decision_wheel_clear_history_title),
            message = stringResource(R.string.decision_wheel_clear_history_message),
            confirm = stringResource(R.string.decision_wheel_clear_history_confirm),
            onDismiss = { showClearHistoryConfirm = false },
            onConfirm = { showClearHistoryConfirm = false; onClearHistory() },
        )
    }
}

@Composable
private fun WheelPanel(
    state: DecisionWheelUiState,
    rotation: Float,
    onRotationDelta: (Float) -> Unit,
    onDragFinished: () -> Unit,
    onSpin: () -> Unit,
) {
    val enabled = state.settings.options.size >= DecisionWheelPolicy.MIN_OPTIONS && !state.spinning
    val separator = stringResource(R.string.decision_wheel_list_separator)
    val optionDescription = state.settings.options.joinToString(separator) { it.label }
    val wheelDescription = stringResource(R.string.decision_wheel_accessibility, optionDescription)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .semantics { contentDescription = wheelDescription }
                    .dragRotation(enabled, onDragFinished) { onRotationDelta(it) },
                contentAlignment = Alignment.Center,
            ) {
                DecisionWheelCanvas(
                    options = state.settings.options,
                    rotation = rotation,
                    selectedIndex = state.lastResult?.let { result ->
                        state.settings.options.indexOfFirst { it.label == result.label }.takeIf { it >= 0 }
                    },
                )
                Surface(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled, role = Role.Button, onClick = onSpin),
                    shape = CircleShape,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                if (state.spinning) R.string.decision_wheel_spinning
                                else R.string.decision_wheel_spin,
                            ),
                            color = if (enabled) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.dragRotation(
    enabled: Boolean,
    onDragFinished: () -> Unit,
    onDelta: (Float) -> Unit,
): Modifier =
    if (!enabled) this else this.then(
        Modifier.pointerInput(enabled) {
            var previousAngle: Float? = null
            fun angleOf(offset: Offset): Float {
                val center = Offset(size.width / 2f, size.height / 2f)
                val degrees = Math.toDegrees(
                    atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble()),
                ).toFloat()
                return if (degrees < 0f) degrees + 360f else degrees
            }
            detectDragGestures(
                onDragStart = { previousAngle = angleOf(it) },
                onDragEnd = { previousAngle = null; onDragFinished() },
                onDragCancel = { previousAngle = null; onDragFinished() },
                onDrag = { change, _ ->
                    val current = angleOf(change.position)
                    val before = previousAngle
                    if (before != null) {
                        var delta = current - before
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        if (abs(delta) > 0.2f) onDelta(delta)
                    }
                    previousAngle = current
                    change.consume()
                },
            )
        },
    )

@Composable
private fun ResultCard(result: WheelResult, time: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.decision_wheel_result_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                ToolboxTextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.decision_wheel_dismiss_result))
                }
            }
            Text(
                result.label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.decision_wheel_result_time, time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun PresetButton(label: Int, enabled: Boolean, onClick: () -> Unit) {
    ToolboxOutlinedButton(onClick = onClick, enabled = enabled) { Text(stringResource(label)) }
}

@Composable
private fun OptionRow(
    index: Int,
    option: WheelOption,
    share: Int,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val editDescription = stringResource(R.string.decision_wheel_edit_option_accessibility, option.label)
    val deleteDescription = stringResource(R.string.decision_wheel_delete_option_accessibility, option.label)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.decision_wheel_option_indexed_label, index + 1, option.label),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.decision_wheel_weight_and_share, option.weight, share),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ToolboxTextButton(
            onClick = onEdit,
            modifier = Modifier.semantics { contentDescription = editDescription },
        ) { Text(stringResource(R.string.decision_wheel_edit)) }
        ToolboxTextButton(
            onClick = onDelete,
            enabled = canDelete,
            modifier = Modifier.semantics { contentDescription = deleteDescription },
        ) {
            Text(stringResource(R.string.decision_wheel_delete), color = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ToolboxSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun HistoryRow(index: Int, result: WheelResult, time: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 20.dp),
        )
        Text(
            result.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OptionEditorDialog(
    option: WheelOption?,
    error: DecisionWheelError?,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var label by rememberSaveable(option) { mutableStateOf(option?.label.orEmpty()) }
    var weight by rememberSaveable(option) { mutableIntStateOf(option?.weight ?: 1) }
    val decreaseDescription = stringResource(R.string.decision_wheel_weight_decrease_description)
    val increaseDescription = stringResource(R.string.decision_wheel_weight_increase_description)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (option == null) R.string.decision_wheel_add_title else R.string.decision_wheel_edit_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolboxTextField(
                    value = label,
                    onValueChange = { value ->
                        label = DecisionWheelPolicy.limitLabel(value)
                        onClearError()
                    },
                    label = stringResource(R.string.decision_wheel_label),
                    placeholder = stringResource(R.string.decision_wheel_label_hint),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        if (error != null) OptionErrorText(error)
                        else Text(
                            stringResource(
                                R.string.decision_wheel_label_count,
                                label.codePointCount(0, label.length),
                                DecisionWheelPolicy.MAX_LABEL_CODE_POINTS,
                            ),
                        )
                    },
                )
                Text(stringResource(R.string.decision_wheel_weight), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolboxOutlinedButton(
                        onClick = { weight = (weight - 1).coerceAtLeast(DecisionWheelPolicy.MIN_WEIGHT); onClearError() },
                        enabled = weight > DecisionWheelPolicy.MIN_WEIGHT,
                        modifier = Modifier.semantics { contentDescription = decreaseDescription },
                    ) {
                        Text(stringResource(R.string.decision_wheel_weight_decrease))
                    }
                    Text(
                        text = stringResource(R.string.decision_wheel_weight_value, weight),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ToolboxOutlinedButton(
                        onClick = { weight = (weight + 1).coerceAtMost(DecisionWheelPolicy.MAX_WEIGHT); onClearError() },
                        enabled = weight < DecisionWheelPolicy.MAX_WEIGHT,
                        modifier = Modifier.semantics { contentDescription = increaseDescription },
                    ) {
                        Text(stringResource(R.string.decision_wheel_weight_increase))
                    }
                }
                Text(
                    stringResource(R.string.decision_wheel_weight_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            ToolboxButton(onClick = { onSave(label, weight) }) {
                Text(stringResource(R.string.decision_wheel_save))
            }
        },
        dismissButton = {
            ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.decision_wheel_cancel)) }
        },
    )
}

@Composable
private fun OptionErrorText(error: DecisionWheelError) {
    Text(
        stringResource(
            when (error) {
                DecisionWheelError.EMPTY -> R.string.decision_wheel_error_empty
                DecisionWheelError.LENGTH -> R.string.decision_wheel_error_length
                DecisionWheelError.DUPLICATE -> R.string.decision_wheel_error_duplicate
                DecisionWheelError.WEIGHT -> R.string.decision_wheel_error_weight
                DecisionWheelError.LIMIT -> R.string.decision_wheel_error_limit
                DecisionWheelError.NOT_ENOUGH -> R.string.decision_wheel_error_not_enough
            },
        ),
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { ToolboxButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.decision_wheel_cancel)) } },
    )
}
