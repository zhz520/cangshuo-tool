package com.cangshuo.toolbox.feature.noise.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxBadge
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.feature.noise.domain.NoiseError
import com.cangshuo.toolbox.feature.noise.domain.NoisePolicy
import com.cangshuo.toolbox.feature.noise.domain.NoisePreset
import com.cangshuo.toolbox.feature.noise.domain.NoiseType
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import java.util.Locale

@Composable
fun NoiseScreen(
    state: NoiseUiState,
    onToggleLayer: (NoiseType) -> Unit,
    onLevel: (NoiseType, Float) -> Unit,
    onMaster: (Float) -> Unit,
    onTimer: (Int) -> Unit,
    onPreset: (NoisePreset) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRefreshPlayback: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var notificationGranted by rememberSaveable {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted
        if (granted) onRefreshPlayback()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            notificationGranted = Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = ToolboxDimens.pageGutter,
                top = ToolboxDimens.pageTop,
                end = ToolboxDimens.pageGutter,
                bottom = ToolboxDimens.pageBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("player") {
                NoisePlayerCard(
                    state = state,
                    onStart = onStart,
                    onPause = onPause,
                    onResume = onResume,
                    onStop = onStop,
                    onMaster = onMaster,
                )
            }
            if (!notificationGranted && Build.VERSION.SDK_INT >= 33) {
                item("permission") {
                    NoisePermissionCard {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            item("presets") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.noise_presets), style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NoisePreset.entries.filter { it != NoisePreset.NONE }.forEach { preset ->
                            AssistChip(
                                onClick = { onPreset(preset) },
                                label = { Text(noisePresetLabel(preset)) },
                            )
                        }
                    }
                }
            }
            item("timer") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.noise_timer), style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NoisePolicy.timerOptions.forEach { minutes ->
                            FilterChip(
                                selected = state.settings.timerMinutes == minutes,
                                onClick = { onTimer(minutes) },
                                label = {
                                    Text(
                                        if (minutes == 0) stringResource(R.string.noise_timer_off)
                                        else pluralStringResource(R.plurals.noise_minutes, minutes, minutes),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item("layers") {
                NoiseLayerList(
                    state = state,
                    onToggle = onToggleLayer,
                    onLevel = onLevel,
                )
            }
            item("note") {
                Text(
                    stringResource(R.string.noise_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoisePlayerCard(
    state: NoiseUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onMaster: (Float) -> Unit,
) {
    val activeLabels = state.settings.activeTypes.map { noiseTypeLabel(it) }
    val status = when {
        state.isPlaying -> stringResource(R.string.noise_status_playing)
        state.isPaused -> stringResource(R.string.noise_status_paused)
        else -> stringResource(R.string.noise_status_ready)
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        activeLabels.joinToString(" · ").ifBlank { stringResource(R.string.noise_no_layers) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        state.playback.remainingSeconds?.let { seconds ->
                            stringResource(R.string.noise_timer_remaining, formatSeconds(seconds))
                        } ?: stringResource(R.string.noise_timer_free),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ToolboxBadge(status, emphasized = state.isPlaying)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolboxButton(
                    onClick = {
                        when {
                            state.isPlaying -> onPause()
                            state.isPaused -> onResume()
                            else -> onStart()
                        }
                    },
                    enabled = state.hasActiveLayers,
                ) {
                    Text(
                        stringResource(
                            when {
                                state.isPlaying -> R.string.noise_pause
                                state.isPaused -> R.string.noise_resume
                                else -> R.string.noise_play
                            },
                        ),
                    )
                }
                ToolboxOutlinedButton(onClick = onStop, enabled = !state.playback.idle) {
                    Text(stringResource(R.string.noise_stop))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            VolumeControl(
                label = stringResource(R.string.noise_master_volume),
                value = state.settings.masterVolume,
                onValueCommitted = onMaster,
            )
            if (!state.hasActiveLayers) {
                Text(
                    stringResource(R.string.noise_select_layer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.playback.error?.let { error ->
                Text(
                    noiseErrorLabel(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun NoiseLayerList(
    state: NoiseUiState,
    onToggle: (NoiseType) -> Unit,
    onLevel: (NoiseType, Float) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            NoiseType.entries.forEachIndexed { index, type ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val level = state.settings.levels[type] ?: 0f
                val label = noiseTypeLabel(type)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                noiseTypeDescription(type),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ToolboxSwitch(
                            checked = level > 0.001f,
                            onCheckedChange = { onToggle(type) },
                            modifier = Modifier.semantics {
                                contentDescription = label
                            },
                        )
                    }
                    if (level > 0.001f) {
                        VolumeControl(
                            label = stringResource(R.string.noise_layer_volume_for, label),
                            value = level,
                            onValueCommitted = { onLevel(type, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeControl(label: String, value: Float, onValueCommitted: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.noise_volume_percent, (local * 100).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it.coerceIn(0f, 1f) },
            onValueChangeFinished = { onValueCommitted(local) },
            valueRange = 0f..1f,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun NoisePermissionCard(onRequest: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.noise_permission_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.noise_permission_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ToolboxOutlinedButton(onClick = onRequest) {
                    Text(stringResource(R.string.noise_permission_action))
                }
            }
        }
    }
}

@Composable
private fun noiseTypeLabel(type: NoiseType): String = stringResource(
    when (type) {
        NoiseType.WHITE -> R.string.noise_white
        NoiseType.PINK -> R.string.noise_pink
        NoiseType.BROWN -> R.string.noise_brown
        NoiseType.RAIN -> R.string.noise_rain
        NoiseType.OCEAN -> R.string.noise_ocean
        NoiseType.FAN -> R.string.noise_fan
    },
)

@Composable
private fun noiseTypeDescription(type: NoiseType): String = stringResource(
    when (type) {
        NoiseType.WHITE -> R.string.noise_white_desc
        NoiseType.PINK -> R.string.noise_pink_desc
        NoiseType.BROWN -> R.string.noise_brown_desc
        NoiseType.RAIN -> R.string.noise_rain_desc
        NoiseType.OCEAN -> R.string.noise_ocean_desc
        NoiseType.FAN -> R.string.noise_fan_desc
    },
)

@Composable
private fun noisePresetLabel(preset: NoisePreset): String = stringResource(
    when (preset) {
        NoisePreset.FOCUS -> R.string.noise_preset_focus
        NoisePreset.RAIN -> R.string.noise_preset_rain
        NoisePreset.SLEEP -> R.string.noise_preset_sleep
        NoisePreset.NONE -> R.string.noise_timer_off
    },
)

@Composable
private fun noiseErrorLabel(error: NoiseError): String = stringResource(
    when (error) {
        NoiseError.NO_LAYER -> R.string.noise_select_layer_hint
        NoiseError.INVALID_LEVEL, NoiseError.INVALID_TIMER -> R.string.noise_invalid_settings
        NoiseError.AUDIO_UNAVAILABLE -> R.string.noise_audio_unavailable
    },
)

private fun formatSeconds(total: Long): String {
    val safe = total.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}
