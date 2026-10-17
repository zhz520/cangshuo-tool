package com.cangshuo.toolbox.feature.banner.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.banner.domain.BannerDirection
import com.cangshuo.toolbox.feature.banner.domain.BannerError
import com.cangshuo.toolbox.feature.banner.domain.BannerPolicy
import com.cangshuo.toolbox.feature.banner.domain.BannerPreset
import com.cangshuo.toolbox.feature.banner.domain.BannerSettings
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BannerScreen(
    state: BannerUiState,
    onTextChange: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onDirectionChange: (BannerDirection) -> Unit,
    onMirroredChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onResetStyle: () -> Unit,
) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings
    val visual = bannerVisualColors()
    val playable = state.validation == null
    val pointCount = settings.text.codePointCount(0, settings.text.length)
    val fontScale = LocalDensity.current.fontScale
    val previewFontSize = settings.fontSizeSp.coerceAtMost(100f / fontScale)

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
                ToolSectionHeader(stringResource(R.string.banner_preview_title))
                Surface(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    shape = MaterialTheme.shapes.large,
                    color = visual.background,
                ) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        if (playable) {
                            BannerMarquee(
                                text = settings.text.trim(),
                                speedDpPerSecond = settings.speedDpPerSecond,
                                fontSizeSp = previewFontSize,
                                direction = settings.direction,
                                mirrored = settings.mirrored,
                                running = !fullscreen,
                                color = visual.foreground,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.banner_empty),
                                color = visual.foreground.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        item(key = "content") {
            ToolGroupCard(title = stringResource(R.string.banner_content_title)) {
                ToolboxTextField(
                    value = settings.text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.banner_content_label),
                    placeholder = stringResource(R.string.banner_content_hint),
                    isError = state.validation == BannerError.EMPTY && settings.text.isNotEmpty(),
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.banner_character_count,
                                pointCount,
                                BannerPolicy.MAX_TEXT_CODE_POINTS,
                            ),
                        )
                    },
                )
                Text(
                    text = stringResource(R.string.banner_presets_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BannerPreset.entries.forEach { preset ->
                        val text = stringResource(preset.textResource())
                        AssistChip(
                            onClick = { onTextChange(text) },
                            label = { Text(text) },
                        )
                    }
                }
            }
        }

        item(key = "motion") {
            ToolGroupCard(title = stringResource(R.string.banner_motion_title)) {
                BannerSlider(
                    label = stringResource(R.string.banner_speed),
                    valueLabel = stringResource(speedLabelResource(settings.speedDpPerSecond)),
                    value = settings.speedDpPerSecond,
                    range = BannerPolicy.MIN_SPEED..BannerPolicy.MAX_SPEED,
                    onValueChange = onSpeedChange,
                )
                BannerSlider(
                    label = stringResource(R.string.banner_font_size),
                    valueLabel = stringResource(fontSizeLabelResource(settings.fontSizeSp)),
                    value = settings.fontSizeSp,
                    range = BannerPolicy.MIN_FONT_SIZE..BannerPolicy.MAX_FONT_SIZE,
                    onValueChange = onFontSizeChange,
                )
                Text(
                    text = stringResource(R.string.banner_direction),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BannerDirection.entries.forEach { direction ->
                        BannerOption(
                            label = stringResource(direction.labelResource()),
                            selected = settings.direction == direction,
                            modifier = Modifier.weight(1f),
                            onClick = { onDirectionChange(direction) },
                        )
                    }
                }
                ToggleRow(
                    title = stringResource(R.string.banner_mirror),
                    subtitle = stringResource(R.string.banner_mirror_hint),
                    checked = settings.mirrored,
                    onCheckedChange = onMirroredChange,
                )
                ToolboxDivider(startIndent = 0.dp)
                ToggleRow(
                    title = stringResource(R.string.banner_keep_screen),
                    subtitle = stringResource(R.string.banner_keep_screen_hint),
                    checked = settings.keepScreenOn,
                    onCheckedChange = onKeepScreenOnChange,
                )
            }
        }

        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolboxButton(
                    onClick = { fullscreen = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = playable,
                ) { Text(stringResource(R.string.banner_play)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToolboxTextButton(onClick = onClear, enabled = settings.text.isNotEmpty()) {
                        Text(stringResource(R.string.banner_clear))
                    }
                    ToolboxTextButton(onClick = onResetStyle) {
                        Text(stringResource(R.string.banner_reset_style))
                    }
                }
            }
        }

        item(key = "note") {
            Text(
                text = stringResource(R.string.banner_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (fullscreen) {
        FullscreenBanner(settings = settings, onDismiss = { fullscreen = false })
    }
}

@Composable
private fun BannerSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueLabel
            },
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
private fun BannerOption(
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
private fun FullscreenBanner(settings: BannerSettings, onDismiss: () -> Unit) {
    var running by rememberSaveable { mutableStateOf(true) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    val visual = bannerVisualColors()
    val description = stringResource(R.string.banner_description_accessibility, settings.text.trim())
    val toggleControlsLabel = stringResource(R.string.banner_toggle_controls)
    LaunchedEffect(controlsVisible, running, interaction) {
        if (controlsVisible && running) {
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
        FullscreenWindowEffect(settings.keepScreenOn)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(visual.background)
                .clickable(
                    onClickLabel = toggleControlsLabel,
                    role = Role.Button,
                ) {
                    controlsVisible = !controlsVisible
                    interaction++
                },
        ) {
            BannerMarquee(
                text = settings.text.trim(),
                speedDpPerSecond = settings.speedDpPerSecond,
                fontSizeSp = settings.fontSizeSp,
                direction = settings.direction,
                mirrored = settings.mirrored,
                running = running,
                color = visual.foreground,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
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
                            stringResource(R.string.banner_fullscreen_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.banner_fullscreen_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.banner_fullscreen_summary,
                                stringResource(settings.direction.labelResource()),
                                stringResource(speedLabelResource(settings.speedDpPerSecond)),
                                stringResource(fontSizeLabelResource(settings.fontSizeSp)),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolboxButton(
                                onClick = {
                                    running = !running
                                    controlsVisible = true
                                    interaction++
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(if (running) R.string.banner_pause else R.string.banner_resume)) }
                            ToolboxOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.banner_exit))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenWindowEffect(keepScreenOn: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        val window = (view.parent as? DialogWindowProvider)?.window
            ?: context.findActivity()?.window
        val controller = window?.let { target -> WindowCompat.getInsetsController(target, view) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class BannerVisualColors(val background: Color, val foreground: Color)

@Composable
private fun bannerVisualColors(): BannerVisualColors {
    val scheme = MaterialTheme.colorScheme
    val background = if (isSystemInDarkTheme()) scheme.surfaceContainerLowest else scheme.inverseSurface
    val accent = scheme.primary
    val foreground = if (contrastRatio(accent, background) >= 3f) accent else scheme.tertiary
    return BannerVisualColors(background, foreground)
}

private fun contrastRatio(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + 0.05f) / (darker + 0.05f)
}

@StringRes
private fun BannerDirection.labelResource(): Int = when (this) {
    BannerDirection.LEFT_TO_RIGHT -> R.string.banner_direction_ltr
    BannerDirection.RIGHT_TO_LEFT -> R.string.banner_direction_rtl
}

@StringRes
private fun speedLabelResource(value: Float): Int = when {
    value < 60f -> R.string.banner_speed_slow
    value < 150f -> R.string.banner_speed_standard
    else -> R.string.banner_speed_fast
}

@StringRes
private fun fontSizeLabelResource(value: Float): Int = when {
    value < 56f -> R.string.banner_font_small
    value < 100f -> R.string.banner_font_standard
    else -> R.string.banner_font_large
}

@StringRes
private fun BannerPreset.textResource(): Int = when (this) {
    BannerPreset.WELCOME -> R.string.banner_preset_welcome
    BannerPreset.PICKUP -> R.string.banner_preset_pickup
    BannerPreset.BIRTHDAY -> R.string.banner_preset_birthday
    BannerPreset.MAKE_WAY -> R.string.banner_preset_make_way
}
