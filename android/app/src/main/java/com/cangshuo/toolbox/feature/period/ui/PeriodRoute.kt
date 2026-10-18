package com.cangshuo.toolbox.feature.period.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@Composable
fun PeriodRoute(factory: ViewModelProvider.Factory) {
    val model: PeriodViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(model) {
        onDispose { model.consumeMessage() }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                model.refreshToday()
                delay(60_000L)
            }
        }
    }
    PeriodScreen(
        state = state,
        onPreviousMonth = model::previousMonth,
        onNextMonth = model::nextMonth,
        onToday = model::goToday,
        onSelectDay = model::selectDay,
        onOpenEditor = model::openEditorForSelectedDay,
        onEditCycle = model::editCycle,
        onDismissEditor = model::dismissEditor,
        onEditorStart = model::setEditorStart,
        onEditorEnd = model::setEditorEnd,
        onFlow = model::setFlow,
        onSymptom = model::toggleSymptom,
        onMood = model::setMood,
        onNotes = model::setNotes,
        onSaveEditor = model::saveEditor,
        onDeleteEditor = model::deleteEditorCycle,
        onOpenSettings = model::openSettings,
        onDismissSettings = model::dismissSettings,
        onCycleDays = model::setCycleDays,
        onPeriodDays = model::setPeriodDays,
        onSaveSettings = model::saveSettings,
        onClearEditorError = model::clearEditorError,
        onClearSettingsError = model::clearSettingsError,
        onMessageConsumed = model::consumeMessage,
    )
}
