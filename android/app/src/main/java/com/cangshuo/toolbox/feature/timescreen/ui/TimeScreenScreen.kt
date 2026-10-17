package com.cangshuo.toolbox.feature.timescreen.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.ToolboxFullscreenWindowEffect
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.toolboxDisplayColors
import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenPolicy
import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenSettings
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import kotlinx.coroutines.delay

@Composable
fun TimeScreenScreen(
    state: TimeScreenUiState,
    onUse24Hour: (Boolean) -> Unit,
    onShowSeconds: (Boolean) -> Unit,
    onShowDate: (Boolean) -> Unit,
    onShowZone: (Boolean) -> Unit,
    onAntiBurnIn: (Boolean) -> Unit,
    onDriftRadius: (Int) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings
    val visual = toolboxDisplayColors()
    val timeDescription = state.snapshot.clock.period?.let { period ->
        stringResource(R.string.time_screen_clock_with_period, state.snapshot.clock.main, period)
    } ?: state.snapshot.clock.main

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
        item(key = "preview") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolSectionHeader(stringResource(R.string.time_screen_preview_title))
                Surface(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    shape = MaterialTheme.shapes.large,
                    color = visual.background,
                ) {
                    TimeDisplay(
                        settings = settings,
                        snapshot = state.snapshot,
                        color = visual.foreground,
                        contentDescription = stringResource(
                            R.string.time_screen_accessibility,
                            timeDescription,
                            state.snapshot.date,
                            state.snapshot.zone,
                        ),
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
                }
            }
        }

        item(key = "format") {
            ToolGroupCard(title = stringResource(R.string.time_screen_format_title)) {
                Text(
                    stringResource(R.string.time_screen_clock_format),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeScreenOption(
                        label = stringResource(R.string.time_screen_24_hour),
                        selected = settings.use24Hour,
                        modifier = Modifier.weight(1f),
                        onClick = { onUse24Hour(true) },
                    )
                    TimeScreenOption(
                        label = stringResource(R.string.time_screen_12_hour),
                        selected = !settings.use24Hour,
                        modifier = Modifier.weight(1f),
                        onClick = { onUse24Hour(false) },
                    )
                }
                ToggleRow(
                    title = stringResource(R.string.time_screen_show_seconds),
                    subtitle = stringResource(R.string.time_screen_show_seconds_hint),
                    checked = settings.showSeconds,
                    onCheckedChange = onShowSeconds,
                )
                ToggleRow(
                    title = stringResource(R.string.time_screen_show_date),
                    subtitle = stringResource(R.string.time_screen_show_date_hint),
                    checked = settings.showDate,
                    onCheckedChange = onShowDate,
                )
                ToggleRow(
                    title = stringResource(R.string.time_screen_show_zone),
                    subtitle = stringResource(R.string.time_screen_show_zone_hint),
                    checked = settings.showZone,
                    onCheckedChange = onShowZone,
                )
            }
        }

        item(key = "display") {
            ToolGroupCard(title = stringResource(R.string.time_screen_display_title)) {
                ToggleRow(
                    title = stringResource(R.string.time_screen_anti_burn),
                    subtitle = stringResource(R.string.time_screen_anti_burn_hint),
                    checked = settings.antiBurnIn,
                    onCheckedChange = onAntiBurnIn,
                )
                if (settings.antiBurnIn) {
                    val driftLabel = driftLabel(settings.driftRadiusDp)
                    val driftTitle = stringResource(R.string.time_screen_drift_radius)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(driftTitle)
                        Text(
                            driftLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = settings.driftRadiusDp.toFloat(),
                        onValueChange = { onDriftRadius(it.toInt()) },
                        valueRange = TimeScreenPolicy.MIN_DRIFT_RADIUS.toFloat()..
                            TimeScreenPolicy.MAX_DRIFT_RADIUS.toFloat(),
                        steps = TimeScreenPolicy.MAX_DRIFT_RADIUS - TimeScreenPolicy.MIN_DRIFT_RADIUS - 1,
                        modifier = Modifier.semantics {
                            contentDescription = driftTitle
                            stateDescription = driftLabel
                        },
                    )
                }
                ToolboxDivider(startIndent = 0.dp)
                ToggleRow(
                    title = stringResource(R.string.time_screen_keep_screen),
                    subtitle = stringResource(R.string.time_screen_keep_screen_hint),
                    checked = settings.keepScreenOn,
                    onCheckedChange = onKeepScreenOn,
                )
            }
        }

        item(key = "actions") {
            ToolboxButton(
                onClick = { fullscreen = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.time_screen_play)) }
        }
        item(key = "reset") {
            ToolboxTextButton(onClick = onReset) {
                Text(stringResource(R.string.time_screen_reset))
            }
        }
        item(key = "note") {
            Text(
                text = stringResource(R.string.time_screen_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "bottom") { Spacer(Modifier.height(ToolboxDimens.pageBottom)) }
    }

    if (fullscreen) {
        FullscreenClock(
            state = state,
            onDismiss = { fullscreen = false },
            onToggleSeconds = { onShowSeconds(!settings.showSeconds) },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ToolboxSwitch(checked, null)
    }
}

@Composable
private fun TimeScreenOption(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val selectedModifier = modifier.semantics { this.selected = selected }
    if (selected) ToolboxButton(onClick, selectedModifier) { Text(label) }
    else ToolboxOutlinedButton(onClick, selectedModifier) { Text(label) }
}

@Composable
private fun FullscreenClock(
    state: TimeScreenUiState,
    onDismiss: () -> Unit,
    onToggleSeconds: () -> Unit,
) {
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    val visual = toolboxDisplayColors()
    val toggleLabel = stringResource(R.string.time_screen_toggle_controls)
    val timeDescription = state.snapshot.clock.period?.let { period ->
        stringResource(R.string.time_screen_clock_with_period, state.snapshot.clock.main, period)
    } ?: state.snapshot.clock.main
    val description = stringResource(
        R.string.time_screen_accessibility,
        timeDescription,
        state.snapshot.date,
        state.snapshot.zone,
    )
    LaunchedEffect(controlsVisible, interaction) {
        if (controlsVisible) {
            delay(4_000L)
            controlsVisible = false
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        ToolboxFullscreenWindowEffect(state.settings.keepScreenOn)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(visual.background)
                .clickable(onClickLabel = toggleLabel, role = Role.Button) {
                    controlsVisible = !controlsVisible
                    interaction++
                },
        ) {
            TimeDisplay(
                settings = state.settings,
                snapshot = state.snapshot,
                color = visual.foreground,
                contentDescription = description,
                modifier = Modifier.fillMaxSize().padding(20.dp),
                driftX = state.drift.xDp,
                driftY = state.drift.yDp,
            )
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.time_screen_fullscreen_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.time_screen_fullscreen_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolboxButton(
                                onClick = {
                                    onToggleSeconds()
                                    controlsVisible = true
                                    interaction++
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(if (state.settings.showSeconds)
                                R.string.time_screen_hide_seconds else R.string.time_screen_show_seconds))
                            }
                            ToolboxOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.time_screen_exit))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun driftLabel(radiusDp: Int): String = stringResource(
    when {
        radiusDp < 6 -> R.string.time_screen_drift_small
        radiusDp < 11 -> R.string.time_screen_drift_medium
        else -> R.string.time_screen_drift_large
    },
)
