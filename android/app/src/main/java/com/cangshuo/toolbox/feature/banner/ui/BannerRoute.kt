package com.cangshuo.toolbox.feature.banner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BannerRoute(factory: ViewModelProvider.Factory) {
    val model: BannerViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    BannerScreen(
        state = state,
        onTextChange = model::setText,
        onSpeedChange = model::setSpeed,
        onFontSizeChange = model::setFontSize,
        onDirectionChange = model::setDirection,
        onMirroredChange = model::setMirrored,
        onKeepScreenOnChange = model::setKeepScreenOn,
        onClear = model::clearText,
        onResetStyle = model::resetStyle,
    )
}
