package com.cangshuo.toolbox.feature.timer.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxBadge
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxDangerButton
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.timer.domain.CountdownPolicy
import com.cangshuo.toolbox.feature.timer.domain.CountdownStatus
import com.cangshuo.toolbox.feature.timer.domain.TimerError
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun TimerScreen(
    state: TimerUiState,
    onHours: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onSeconds: (String) -> Unit,
    onLabel: (String) -> Unit,
    onPreset: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onExtend: () -> Unit,
    onKeepScreen: (Boolean) -> Unit,
    onRefreshService: () -> Unit,
    onClearError: () -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val locale = LocalConfiguration.current.locales[0]
    val finishFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { timerMessageLabel(it) }
    var confirmReset by remember { mutableStateOf(false) }
    var notificationGranted by rememberSaveable {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val granted = Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (granted != notificationGranted) {
                notificationGranted = granted
                if (granted) onRefreshService()
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationGranted = granted
        permissionRequested = true
        if (granted) {
            onRefreshService()
        }
    }
    LaunchedEffect(state.messageRevision) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onMessageConsumed()
        }
    }
    DisposableEffect(state.running, state.keepScreenOn) {
        view.keepScreenOn = state.running && state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = ToolboxDimens.pageGutter,
                top = ToolboxDimens.pageTop,
                end = ToolboxDimens.pageGutter,
                bottom = ToolboxDimens.pageBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("clock") {
                TimerClockCard(
                    state = state,
                    finishText = state.finishAtWallMillis?.let { millis ->
                        stringResource(
                            R.string.countdown_finish_at,
                            finishFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())),
                        )
                    },
                    onStart = onStart,
                    onPause = onPause,
                    onResume = onResume,
                    onReset = { confirmReset = true },
                    onExtend = onExtend,
                )
            }
            if (!notificationGranted && Build.VERSION.SDK_INT >= 33) {
                item("permission") {
                    val activity = context as? Activity
                    val canAskAgain = !permissionRequested || activity?.let {
                        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
                    } == true
                    TimerPermissionCard(
                        actionLabel = stringResource(
                            if (canAskAgain) R.string.countdown_permission_action
                            else R.string.countdown_open_notification_settings,
                        ),
                    ) {
                        if (canAskAgain) {
                            permissionRequested = true
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                                    Settings.EXTRA_APP_PACKAGE,
                                    context.packageName,
                                ),
                            )
                        }
                    }
                }
            }
            if (state.durationEditable) {
                item("presets") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolSectionHeader(stringResource(R.string.countdown_presets))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CountdownPolicy.presetsMinutes.forEach { minutes ->
                                AssistChip(
                                    onClick = { onPreset(minutes) },
                                    label = {
                                        Text(stringResource(R.string.countdown_minutes, minutes))
                                    },
                                )
                            }
                        }
                    }
                }
                item("duration") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolSectionHeader(stringResource(R.string.countdown_custom_duration))
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val largeText = LocalDensity.current.fontScale > 1.3f
                            val narrow = maxWidth < 360.dp
                            val fieldWidth = when {
                                largeText -> maxWidth
                                narrow -> (maxWidth - 8.dp) / 2
                                else -> (maxWidth - 16.dp) / 3
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ToolboxTextField(
                                    value = state.hours,
                                    onValueChange = { onHours(it); onClearError() },
                                    modifier = Modifier.width(fieldWidth),
                                    label = stringResource(R.string.countdown_hours),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = state.error == TimerError.DURATION,
                                )
                                ToolboxTextField(
                                    value = state.minutes,
                                    onValueChange = { onMinutes(it); onClearError() },
                                    modifier = Modifier.width(fieldWidth),
                                    label = stringResource(R.string.countdown_minutes_label),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = state.error == TimerError.DURATION,
                                )
                                ToolboxTextField(
                                    value = state.seconds,
                                    onValueChange = { onSeconds(it); onClearError() },
                                    modifier = Modifier.width(fieldWidth),
                                    label = stringResource(R.string.countdown_seconds),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = state.error == TimerError.DURATION,
                                )
                            }
                        }
                        state.error?.takeIf { it == TimerError.DURATION }?.let { error ->
                            Text(
                                timerErrorLabel(error),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            item("label") {
                ToolboxTextField(
                    value = state.label,
                    onValueChange = { onLabel(it); onClearError() },
                    label = stringResource(R.string.countdown_label),
                    placeholder = stringResource(R.string.countdown_label_hint),
                    enabled = state.durationEditable,
                    isError = state.error == TimerError.LABEL,
                    supportingText = if (state.error == TimerError.LABEL) {
                        {
                            Text(
                                timerErrorLabel(TimerError.LABEL),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            )
                        }
                    } else null,
                )
            }
            item("options") {
                val keepScreenLabel = stringResource(R.string.countdown_keep_screen)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(keepScreenLabel, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.countdown_keep_screen_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ToolboxSwitch(
                        checked = state.keepScreenOn,
                        onCheckedChange = onKeepScreen,
                        modifier = Modifier.semantics {
                            contentDescription = keepScreenLabel
                        },
                    )
                }
            }
            item("note") {
                Text(
                    stringResource(R.string.countdown_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
    if (confirmReset) {
        TimerResetDialog(
            onDismiss = { confirmReset = false },
            onConfirm = { confirmReset = false; onReset() },
        )
    }
}

@Composable
private fun TimerClockCard(
    state: TimerUiState,
    finishText: String?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onExtend: () -> Unit,
) {
    val statusText = when (state.status) {
        CountdownStatus.IDLE -> stringResource(R.string.countdown_ready)
        CountdownStatus.RUNNING -> stringResource(R.string.countdown_running)
        CountdownStatus.PAUSED -> stringResource(R.string.countdown_paused)
        CountdownStatus.FINISHED -> stringResource(R.string.countdown_finished)
    }
    val clock = CountdownPolicy.format(state.remainingMillis)
    val clockStyle = if (LocalDensity.current.fontScale > 1.5f) {
        MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.displaySmall
    }
    val progressDescription = stringResource(
        R.string.countdown_progress_description,
        (state.progress * 100).toInt(),
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stackHeader = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f
                if (stackHeader) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.countdown_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        ToolboxBadge(statusText, emphasized = state.running)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.countdown_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        ToolboxBadge(statusText, emphasized = state.running)
                    }
                }
            }
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxSize().semantics {
                        contentDescription = progressDescription
                        progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                            current = state.progress,
                            range = 0f..1f,
                        )
                    },
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth(0.74f), contentAlignment = Alignment.Center) {
                        Text(
                            clock,
                            style = clockStyle,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    if (state.label.isNotBlank()) {
                        Text(
                            state.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                    finishText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolboxButton(
                    onClick = when {
                        state.running -> onPause
                        state.paused -> onResume
                        else -> onStart
                    },
                ) {
                    Text(
                        stringResource(
                            when {
                                state.running -> R.string.countdown_pause
                                state.paused -> R.string.countdown_resume
                                state.finished -> R.string.countdown_restart
                                else -> R.string.countdown_start
                            },
                        ),
                    )
                }
                if (state.running || state.paused) {
                    ToolboxOutlinedButton(onClick = onExtend) {
                        Text(stringResource(R.string.countdown_add_minute))
                    }
                }
                ToolboxOutlinedButton(onClick = onReset, enabled = state.status != CountdownStatus.IDLE) {
                    Text(stringResource(R.string.countdown_reset))
                }
            }
        }
    }
}

@Composable
private fun TimerPermissionCard(actionLabel: String, onRequest: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.countdown_permission_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.countdown_permission_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ToolboxOutlinedButton(onClick = onRequest) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun TimerResetDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.countdown_reset_title)) },
        text = {
            Text(
                stringResource(R.string.countdown_reset_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            ToolboxDangerButton(onClick = onConfirm) { Text(stringResource(R.string.countdown_reset)) }
        },
        dismissButton = {
            ToolboxTextButton(onClick = onDismiss) { Text(stringResource(R.string.countdown_cancel)) }
        },
    )
}

@Composable
private fun timerErrorLabel(error: TimerError): String = stringResource(
    when (error) {
        TimerError.DURATION -> R.string.countdown_error_duration
        TimerError.LABEL -> R.string.countdown_error_label
        TimerError.STORAGE -> R.string.countdown_error_storage
    },
)

@Composable
private fun timerMessageLabel(message: TimerMessage): String = stringResource(
    when (message) {
        TimerMessage.STARTED -> R.string.countdown_started
        TimerMessage.PAUSED -> R.string.countdown_paused_message
        TimerMessage.RESUMED -> R.string.countdown_resumed_message
        TimerMessage.RESET -> R.string.countdown_reset_message
        TimerMessage.EXTENDED -> R.string.countdown_extended_message
        TimerMessage.FINISHED -> R.string.countdown_finished_message
        TimerMessage.STORAGE_FAILED -> R.string.countdown_error_storage
    },
)
