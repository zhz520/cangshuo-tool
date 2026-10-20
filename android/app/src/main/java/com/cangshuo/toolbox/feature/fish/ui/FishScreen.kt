package com.cangshuo.toolbox.feature.fish.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.*
import com.cangshuo.toolbox.feature.fish.domain.*
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import kotlin.math.min

@Composable
fun FishRoute(factory: ViewModelProvider.Factory) {
    val model: FishViewModel = viewModel(factory = factory)
    val ui by model.state.collectAsStateWithLifecycle()
    val stored = ui.stored
    LifecycleResumeEffect(model) { model.foreground(); onPauseOrDispose { model.background() } }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val bounce = remember { Animatable(1f) }
    val settings = stored?.settings ?: FishSettings()
    LaunchedEffect(ui.pulse, settings.motion) {
        if (ui.pulse == 0L) return@LaunchedEffect
        if (!settings.motion) { bounce.snapTo(1f); return@LaunchedEffect }
        bounce.snapTo(.93f)
        bounce.animateTo(1.03f, tween(55))
        bounce.animateTo(1f, spring(dampingRatio = .58f, stiffness = 520f))
    }
    val total = stored?.total ?: 0
    val today = stored?.today ?: 0
    val goal = settings.goal
    val progress = (today.toFloat() / goal).coerceIn(0f, 1f)
    val report = stored?.let {
        buildString {
            append(stringResource(R.string.fish_name)).append('\n')
            append(stringResource(R.string.fish_total_label)).append(": ").append(it.total).append('\n')
            append(stringResource(R.string.fish_today_label)).append(": ").append(it.today).append('\n')
            append(stringResource(R.string.fish_goal_label)).append(": ").append(it.settings.goal).append('\n')
            append(stringResource(R.string.fish_achievements_label)).append(": ").append(it.unlocked.size).append('/').append(FishPolicy.achievements.size)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(ToolboxDimens.pageGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.fish_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FishMetric(stringResource(R.string.fish_total_label), total.toString(), Modifier.weight(1f))
                FishMetric(stringResource(R.string.fish_today_label), today.toString(), Modifier.weight(1f))
            }
            Text(stringResource(R.string.fish_goal_progress, today, goal), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
            val action = stringResource(R.string.fish_tap_action, total)
            val stage = MaterialTheme.colorScheme.surfaceContainer
            val accent = MaterialTheme.colorScheme.primary
            val ink = MaterialTheme.colorScheme.onSurface
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Box(Modifier.fillMaxWidth().heightIn(min = 230.dp).clip(MaterialTheme.shapes.large)
                .background(stage).clickable(enabled = stored != null && !ui.limitReached, role = Role.Button, onClick = model::tap)
                .semantics(mergeDescendants = true) { contentDescription = action; if (ui.limitReached) disabled() }
                .padding(20.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Canvas(Modifier.size(150.dp).scale(bounce.value)) {
                        val w = size.width * .72f
                        val h = size.height * .50f
                        val left = (size.width - w) / 2
                        val top = (size.height - h) / 2 + 10.dp.toPx()
                        drawOval(stage, Offset(left, top), Size(w, h))
                        drawOval(ink, Offset(left, top), Size(w, h), style = Stroke(3.dp.toPx()))
                        drawOval(accent, Offset(left, top), Size(w, h), style = Stroke(1.dp.toPx()))
                        drawCircle(ink, 3.dp.toPx(), Offset(left + w * .25f, top + h * .38f))
                        drawLine(ink, Offset(left + w * .43f, top + h * .18f), Offset(left + w * .56f, top + h * .68f), 3.dp.toPx())
                        drawLine(accent, Offset(left + w * .48f, top + h * .22f), Offset(left + w * .60f, top + h * .72f), 1.dp.toPx())
                        drawLine(accent, Offset(left + w * .70f, top - 18.dp.toPx()), Offset(left + w * .91f, top + 8.dp.toPx()), 5.dp.toPx())
                    }
                    Text(total.toString(), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
                    Text(if (ui.autoEnabled) stringResource(R.string.fish_auto_running) else stringResource(R.string.fish_tap_hint),
                        style = MaterialTheme.typography.bodySmall, color = muted, textAlign = TextAlign.Center)
                }
            }
            if (ui.limitReached) Text(stringResource(R.string.fish_limit), color = MaterialTheme.colorScheme.error)
            if (ui.preparing) ToolboxLoadingIndicator(compact = true)
            else if (ui.audioError) Text(stringResource(R.string.fish_audio_error), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            if (ui.newAchievements.isNotEmpty()) {
                ToolGroupCard(title = stringResource(R.string.fish_new_achievement)) {
                    ui.newAchievements.forEach { Text(achievementName(it.threshold), style = MaterialTheme.typography.titleSmall) }
                    ToolboxTextButton(model::dismissAchievements) { Text(stringResource(R.string.fish_dismiss)) }
                }
            }
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.fish_daily_goal))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FishPolicy.goals.forEach { value ->
                    if (value == goal) ToolboxButton({ model.setGoal(value) }) { Text(value.toString()) }
                    else ToolboxOutlinedButton({ model.setGoal(value) }) { Text(value.toString()) }
                }
            }
            FishSettingRow(stringResource(R.string.fish_sound), settings.sound, model::setSound)
            FishSettingRow(stringResource(R.string.fish_haptic), settings.haptic, model::setHaptic)
            FishSettingRow(stringResource(R.string.fish_motion), settings.motion, model::setMotion)
            FishSettingRow(stringResource(R.string.fish_auto), ui.autoEnabled) { model.toggleAuto() }
            if (ui.autoEnabled) Text(stringResource(R.string.fish_auto_remaining, ((ui.autoRemainingMs + 999) / 1000).toInt()),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FishPolicy.intervals.forEach { interval ->
                    val label = if (interval == 500L) stringResource(R.string.fish_interval_half)
                    else stringResource(R.string.fish_interval, (interval / 1000).toInt())
                    if (interval == settings.autoIntervalMs) ToolboxButton({ model.setInterval(interval) }, enabled = !ui.autoEnabled) { Text(label) }
                    else ToolboxOutlinedButton({ model.setInterval(interval) }, enabled = !ui.autoEnabled) { Text(label) }
                }
            }
            Text(stringResource(R.string.fish_auto_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToolboxDivider()
            ToolSectionHeader(stringResource(R.string.fish_achievements_label))
            FishPolicy.achievements.forEach { achievement ->
                val unlocked = stored?.unlocked?.contains(achievement.threshold) == true
                ToolListItem(title = achievementName(achievement.threshold), subtitle = stringResource(R.string.fish_threshold, achievement.threshold),
                    icon = if (unlocked) R.drawable.ic_star else R.drawable.ic_star_outline,
                    trailing = { if (unlocked) ToolboxBadge(stringResource(R.string.fish_unlocked), true) })
            }
            ToolboxDivider()
            SystemReportActions(report, ui.preparing, {}, showRefresh = false)
            ToolboxDangerButton({ confirmReset = true }, enabled = stored != null && !ui.autoEnabled) { Text(stringResource(R.string.fish_reset)) }
            Text(stringResource(R.string.fish_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ToolboxDimens.pageBottom))
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text(stringResource(R.string.fish_reset)) },
        text = { Text(stringResource(R.string.fish_reset_confirm)) }, confirmButton = {
            ToolboxTextButton({ model.resetAll(); confirmReset = false }) { Text(stringResource(R.string.fish_confirm)) }
        }, dismissButton = { ToolboxTextButton({ confirmReset = false }) { Text(stringResource(R.string.fish_cancel)) } })
}

@Composable
private fun FishMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun FishSettingRow(label: String, checked: Boolean, onClick: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        ToolboxSwitch(checked, null)
    }
}

@Composable
private fun achievementName(threshold: Long): String = stringResource(when (threshold) {
    1L -> R.string.fish_achievement_first; 100L -> R.string.fish_achievement_100
    500L -> R.string.fish_achievement_500; 1_000L -> R.string.fish_achievement_1000
    3_000L -> R.string.fish_achievement_3000; 10_000L -> R.string.fish_achievement_10000
    else -> R.string.fish_achievement_30000
})
