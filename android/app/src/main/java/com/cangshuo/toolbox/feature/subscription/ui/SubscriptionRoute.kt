package com.cangshuo.toolbox.feature.subscription.ui

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
fun SubscriptionRoute(factory: ViewModelProvider.Factory) {
    val model: SubscriptionViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(model) { onDispose { model.consumeMessage() } }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                model.refreshToday()
                delay(60_000L)
            }
        }
    }
    SubscriptionScreen(
        state = state,
        onFilter = model::setFilter,
        onSort = model::setSort,
        onQuery = model::setQuery,
        onSave = model::save,
        onSetActive = model::setActive,
        onRenew = model::renew,
        onDelete = model::delete,
        onClearEditError = model::clearEditError,
        onMessageConsumed = model::consumeMessage,
    )
}
