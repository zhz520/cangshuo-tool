package com.cangshuo.toolbox.feature.pomodoro.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.UnicodeText
import com.cangshuo.toolbox.core.ui.ToolboxBadge
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroError
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPhase
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPolicy
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSession
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSettings
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun PomodoroScreen(
    state: PomodoroUiState,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onSelectPhase: (PomodoroPhase) -> Unit,
    onSaveLabel: (String) -> Unit,
    onSaveSettings: (PomodoroSettings) -> Unit,
    onClearHistory: () -> Unit,
    onClearError: () -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    }
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var startAfterPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted
        if (startAfterPermission) {
            startAfterPermission = false
            onToggle()
        }
    }
    LifecycleResumeEffect(context) {
        notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }
    val messages = mapOf(
        PomodoroMessage.SAVED to stringResource(R.string.pomodoro_settings_saved),
        PomodoroMessage.RESET to stringResource(R.string.pomodoro_reset_done),
        PomodoroMessage.SKIPPED to stringResource(R.string.pomodoro_skipped),
        PomodoroMessage.HISTORY_CLEARED to stringResource(R.string.pomodoro_history_cleared),
        PomodoroMessage.STORAGE_FAILED to stringResource(R.string.pomodoro_storage_failed),
    )
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(messages[message] ?: return@LaunchedEffect)
        onMessageConsumed()
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showLabel by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val hasProgress = state.timer.remainingSeconds < state.timer.durationSeconds ||
        state.timer.sessionStartedAtMillis != null
    val startOrPause = {
        if (state.timer.running) {
            onToggle()
        } else if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startAfterPermission = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onToggle()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize(),
            contentPadding = PaddingValues(
                start = ToolboxDimens.pageGutter,
                top = ToolboxDimens.pageTop,
                end = ToolboxDimens.pageGutter,
                bottom = ToolboxDimens.pageBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) {
                item("loading") { ToolboxLoadingState(stringResource(R.string.pomodoro_ready)) }
                return@LazyColumn
            }
            item("stats") { PomodoroStatsCard(state) }
            item("timer") {
                PomodoroTimerCard(
                    state = state,
                    onStartOrPause = startOrPause,
                    onSkip = onSkip,
                    onResetRequest = { confirmReset = true },
                    onEditLabel = { showLabel = true },
                    resetEnabled = hasProgress && !state.busy,
                    onSelectPhase = onSelectPhase,
                )
            }
            if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item("notification-permission") {
                    PomodoroPermissionCard {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            item("settings-toggle") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ToolboxTextButton(onClick = { showSettings = !showSettings }) {
                        Text(stringResource(R.string.pomodoro_settings))
                    }
                }
            }
            if (showSettings) {
                item("settings") {
                    PomodoroSettingsSection(
                        settings = state.settings,
                        enabled = !state.timer.running && !state.busy,
                        onSave = onSaveSettings,
                    )
                }
            }
            item("history-header") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.pomodoro_history), style = MaterialTheme.typography.titleMedium)
                    if (state.history.isNotEmpty()) {
                        ToolboxTextButton(onClick = { confirmClear = true }, enabled = !state.busy) {
                            Text(stringResource(R.string.pomodoro_clear_history))
                        }
                    }
                }
            }
            if (state.history.isEmpty()) {
                item("history-empty") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            stringResource(R.string.pomodoro_history_empty),
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(state.history.take(30), key = { it.id }) { session ->
                    PomodoroHistoryRow(session, timeFormatter)
                }
            }
            item("note") {
                Text(
                    stringResource(R.string.pomodoro_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (showLabel) {
        PomodoroLabelDialog(
            initial = state.timer.label,
            error = state.error == PomodoroError.LABEL,
            onDismiss = {
                showLabel = false
                onClearError()
            },
            onSave = { label ->
                onSaveLabel(label)
                showLabel = false
            },
            onClearError = onClearError,
        )
    }
    if (confirmReset) {
        PomodoroConfirmDialog(
            title = stringResource(R.string.pomodoro_reset_title),
            message = stringResource(R.string.pomodoro_reset_message),
            confirm = stringResource(R.string.pomodoro_reset_confirm),
            onDismiss = { confirmReset = false },
            onConfirm = {
                confirmReset = false
                onReset()
            },
        )
    }
    if (confirmClear) {
        PomodoroConfirmDialog(
            title = stringResource(R.string.pomodoro_clear_history_title),
            message = stringResource(R.string.pomodoro_clear_history_message),
            confirm = stringResource(R.string.pomodoro_clear_history_confirm),
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                onClearHistory()
            },
        )
    }
}

@Composable
private fun PomodoroStatsCard(state: PomodoroUiState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PomodoroStat(
                    stringResource(R.string.pomodoro_today_focus),
                    stringResource(R.string.pomodoro_today_minutes, state.stats.todayFocusMinutes),
                    Modifier.weight(1f),
                    emphasized = state.stats.todayFocusMinutes > 0,
                )
                PomodoroStat(
                    stringResource(R.string.pomodoro_today_sessions),
                    state.stats.todayCompletedFocuses.toString(),
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PomodoroStat(
                    stringResource(R.string.pomodoro_week_focus),
                    stringResource(R.string.pomodoro_today_minutes, state.stats.weekFocusMinutes),
                    Modifier.weight(1f),
                )
                PomodoroStat(
                    stringResource(R.string.pomodoro_streak),
                    pluralStringResource(
                        R.plurals.pomodoro_days,
                        state.stats.currentStreakDays,
                        state.stats.currentStreakDays,
                    ),
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PomodoroStat(
                    stringResource(R.string.pomodoro_total_focuses),
                    state.stats.completedFocuses.toString(),
                    Modifier.weight(1f),
                )
                PomodoroStat(
                    stringResource(R.string.pomodoro_longest_streak),
                    pluralStringResource(
                        R.plurals.pomodoro_days,
                        state.stats.longestStreakDays,
                        state.stats.longestStreakDays,
                    ),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PomodoroStat(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PomodoroTimerCard(
    state: PomodoroUiState,
    onStartOrPause: () -> Unit,
    onSkip: () -> Unit,
    onResetRequest: () -> Unit,
    onEditLabel: () -> Unit,
    resetEnabled: Boolean,
    onSelectPhase: (PomodoroPhase) -> Unit,
) {
    val timer = state.timer
    val progress = if (timer.durationSeconds == 0) 0f else
        1f - timer.remainingSeconds.toFloat() / timer.durationSeconds
    val phaseLabel = phaseLabel(timer.phase)
    val status = when {
        timer.running -> stringResource(R.string.pomodoro_running)
        timer.remainingSeconds < timer.durationSeconds || timer.sessionStartedAtMillis != null ->
            stringResource(R.string.pomodoro_paused)
        else -> stringResource(R.string.pomodoro_ready)
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PomodoroPhase.entries.forEach { phase ->
                    FilterChip(
                        selected = phase == timer.phase,
                        enabled = !timer.running && !state.busy,
                        onClick = { onSelectPhase(phase) },
                        label = { Text(phaseLabel(phase)) },
                    )
                }
            }
            Text(
                stringResource(
                    R.string.pomodoro_round,
                    PomodoroPolicy.roundLabel(timer, state.settings),
                    state.settings.roundsBeforeLongBreak,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.size(204.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 9.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        PomodoroPolicy.formatClock(timer.remainingSeconds),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolboxBadge(phaseLabel(timer.phase), emphasized = timer.phase == PomodoroPhase.FOCUS)
                Text(
                    timer.label.ifBlank { stringResource(R.string.pomodoro_label_empty) },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (timer.label.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                ToolboxTextButton(onClick = onEditLabel, enabled = !state.busy) {
                    Text(stringResource(R.string.pomodoro_edit_label))
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolboxButton(onClick = onStartOrPause, enabled = !state.busy) {
                    Text(
                        stringResource(
                            when {
                                timer.running -> R.string.pomodoro_pause
                                timer.remainingSeconds < timer.durationSeconds -> R.string.pomodoro_resume
                                else -> R.string.pomodoro_start
                            },
                        ),
                    )
                }
                ToolboxOutlinedButton(onClick = onSkip, enabled = !state.busy) {
                    Text(stringResource(R.string.pomodoro_skip))
                }
                ToolboxTextButton(onClick = onResetRequest, enabled = resetEnabled) {
                    Text(stringResource(R.string.pomodoro_reset))
                }
            }
        }
    }
}

@Composable
private fun phaseLabel(phase: PomodoroPhase): String = stringResource(
    when (phase) {
        PomodoroPhase.FOCUS -> R.string.pomodoro_focus
        PomodoroPhase.SHORT_BREAK -> R.string.pomodoro_short_break
        PomodoroPhase.LONG_BREAK -> R.string.pomodoro_long_break
    },
)

@Composable
private fun PomodoroPermissionCard(onRequest: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.pomodoro_notification_permission_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.pomodoro_notification_permission_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ToolboxOutlinedButton(onClick = onRequest) {
                    Text(stringResource(R.string.pomodoro_notification_permission_action))
                }
            }
        }
    }
}

@Composable
private fun PomodoroSettingsSection(
    settings: PomodoroSettings,
    enabled: Boolean,
    onSave: (PomodoroSettings) -> Unit,
) {
    var focus by rememberSaveable(settings.focusMinutes) { mutableStateOf(settings.focusMinutes.toString()) }
    var short by rememberSaveable(settings.shortBreakMinutes) { mutableStateOf(settings.shortBreakMinutes.toString()) }
    var long by rememberSaveable(settings.longBreakMinutes) { mutableStateOf(settings.longBreakMinutes.toString()) }
    var rounds by rememberSaveable(settings.roundsBeforeLongBreak) {
        mutableStateOf(settings.roundsBeforeLongBreak.toString())
    }
    var autoBreak by rememberSaveable(settings.autoStartBreaks) { mutableStateOf(settings.autoStartBreaks) }
    var autoFocus by rememberSaveable(settings.autoStartFocus) { mutableStateOf(settings.autoStartFocus) }
    var invalid by remember { mutableStateOf(false) }
    fun applyPreset(preset: PomodoroSettings) {
        focus = preset.focusMinutes.toString()
        short = preset.shortBreakMinutes.toString()
        long = preset.longBreakMinutes.toString()
        rounds = preset.roundsBeforeLongBreak.toString()
        invalid = false
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.pomodoro_settings), style = MaterialTheme.typography.titleMedium)
            if (!enabled) {
                Text(
                    stringResource(R.string.pomodoro_settings_locked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    PomodoroSettings(25, 5, 15, 4),
                    PomodoroSettings(50, 10, 20, 4),
                    PomodoroSettings(15, 3, 10, 4),
                ).forEachIndexed { index, preset ->
                    ToolboxOutlinedButton(onClick = { applyPreset(preset) }, enabled = enabled) {
                        Text(
                            stringResource(
                                when (index) {
                                    0 -> R.string.pomodoro_preset_classic
                                    1 -> R.string.pomodoro_preset_deep
                                    else -> R.string.pomodoro_preset_quick
                                },
                            ),
                        )
                    }
                }
            }
            PomodoroNumberField(
                value = focus,
                onValueChange = { focus = it; invalid = false },
                label = stringResource(R.string.pomodoro_focus_minutes),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
            PomodoroNumberField(
                value = short,
                onValueChange = { short = it; invalid = false },
                label = stringResource(R.string.pomodoro_short_minutes),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
            PomodoroNumberField(
                value = long,
                onValueChange = { long = it; invalid = false },
                label = stringResource(R.string.pomodoro_long_minutes),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
            PomodoroNumberField(
                value = rounds,
                onValueChange = { rounds = it; invalid = false },
                label = stringResource(R.string.pomodoro_rounds),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
            SettingSwitchRow(stringResource(R.string.pomodoro_auto_break), autoBreak, enabled) {
                autoBreak = it
            }
            SettingSwitchRow(stringResource(R.string.pomodoro_auto_focus), autoFocus, enabled) {
                autoFocus = it
            }
            if (invalid) {
                Text(
                    stringResource(R.string.pomodoro_settings_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ToolboxButton(
                    onClick = {
                        val next = PomodoroSettings(
                            focusMinutes = focus.toIntOrNull() ?: 0,
                            shortBreakMinutes = short.toIntOrNull() ?: 0,
                            longBreakMinutes = long.toIntOrNull() ?: 0,
                            roundsBeforeLongBreak = rounds.toIntOrNull() ?: 0,
                            autoStartBreaks = autoBreak,
                            autoStartFocus = autoFocus,
                        )
                        if (PomodoroPolicy.validateSettings(next) == null) {
                            invalid = false
                            onSave(next)
                        } else {
                            invalid = true
                        }
                    },
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.pomodoro_apply_settings))
                }
            }
        }
    }
}

@Composable
private fun PomodoroNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
) {
    ToolboxTextField(
        value = value,
        onValueChange = { next -> if (next.length <= 3 && next.all(Char::isDigit)) onValueChange(next) },
        modifier = modifier,
        label = label,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        ToolboxSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun PomodoroHistoryRow(session: PomodoroSession, formatter: DateTimeFormatter) {
    val zone = ZoneId.systemDefault()
    val ended = Instant.ofEpochMilli(session.endedAt).atZone(zone)
    val phase = phaseLabel(session.phase)
    val state = stringResource(if (session.completed) R.string.pomodoro_completed else R.string.pomodoro_incomplete)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    session.label.ifBlank { stringResource(R.string.pomodoro_unnamed) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.pomodoro_history_subtitle,
                        formatter.format(ended),
                        stringResource(R.string.pomodoro_minutes, (session.elapsedSeconds / 60).coerceAtLeast(1)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolboxBadge(phase)
                Text(
                    state,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (session.completed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PomodoroLabelDialog(
    initial: String,
    error: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pomodoro_label_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolboxTextField(
                    value = value,
                    onValueChange = {
                        value = UnicodeText.takeCodePointsWithoutSplittingCluster(
                            it,
                            PomodoroPolicy.MAX_LABEL_CODE_POINTS,
                        )
                        onClearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.pomodoro_edit_label),
                    placeholder = stringResource(R.string.pomodoro_label_hint),
                    isError = error,
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.pomodoro_label_count,
                                value.codePointCount(0, value.length),
                                PomodoroPolicy.MAX_LABEL_CODE_POINTS,
                            ),
                        )
                    },
                )
            }
        },
        confirmButton = {
            ToolboxTextButton(onClick = { onSave(value) }) {
                Text(stringResource(R.string.pomodoro_save))
            }
        },
        dismissButton = {
            ToolboxTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pomodoro_cancel))
            }
        },
    )
}

@Composable
private fun PomodoroConfirmDialog(
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
        confirmButton = {
            ToolboxButton(onClick = onConfirm) { Text(confirm) }
        },
        dismissButton = {
            ToolboxTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pomodoro_cancel))
            }
        },
    )
}
