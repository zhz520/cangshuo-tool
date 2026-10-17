package com.cangshuo.toolbox.feature.banner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.banner.domain.ApplyBannerSettingsUseCase
import com.cangshuo.toolbox.feature.banner.domain.BannerDirection
import com.cangshuo.toolbox.feature.banner.domain.BannerPolicy
import com.cangshuo.toolbox.feature.banner.domain.BannerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BannerViewModel(
    private val applySettings: ApplyBannerSettingsUseCase,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var settings = restoreSettings()
    private val mutable = MutableStateFlow(toUiState(settings))
    val state: StateFlow<BannerUiState> = mutable.asStateFlow()

    fun setText(raw: String) = update(settings.copy(text = applySettings.sanitizeInput(raw)))

    fun setSpeed(value: Float) = update(settings.copy(speedDpPerSecond = value))

    fun setFontSize(value: Float) = update(settings.copy(fontSizeSp = value))

    fun setDirection(direction: BannerDirection) = update(settings.copy(direction = direction))

    fun setMirrored(mirrored: Boolean) = update(settings.copy(mirrored = mirrored))

    fun setKeepScreenOn(enabled: Boolean) = update(settings.copy(keepScreenOn = enabled))

    fun clearText() = update(settings.copy(text = ""))

    fun resetStyle() = update(
        settings.copy(
            speedDpPerSecond = BannerSettings().speedDpPerSecond,
            fontSizeSp = BannerSettings().fontSizeSp,
            direction = BannerSettings().direction,
            mirrored = false,
            keepScreenOn = true,
        ),
    )

    private fun update(next: BannerSettings) {
        settings = BannerPolicy.normalize(next)
        persist(settings)
        mutable.value = toUiState(settings)
    }

    private fun toUiState(value: BannerSettings): BannerUiState =
        BannerUiState(settings = value, validation = applySettings.validate(value))

    private fun restoreSettings(): BannerSettings {
        val direction = saved.get<String>("bnDirection")
            ?.let { runCatching { BannerDirection.valueOf(it) }.getOrNull() }
            ?: BannerDirection.RIGHT_TO_LEFT
        return BannerPolicy.normalize(
            BannerSettings(
                text = applySettings.sanitizeInput(saved.get<String>("bnText").orEmpty()),
                speedDpPerSecond = (saved.get<Float>("bnSpeed") ?: BannerSettings().speedDpPerSecond)
                    .coerceIn(BannerPolicy.MIN_SPEED, BannerPolicy.MAX_SPEED),
                fontSizeSp = (saved.get<Float>("bnFontSize") ?: BannerSettings().fontSizeSp)
                    .coerceIn(BannerPolicy.MIN_FONT_SIZE, BannerPolicy.MAX_FONT_SIZE),
                direction = direction,
                mirrored = saved.get<Boolean>("bnMirror") ?: false,
                keepScreenOn = saved.get<Boolean>("bnKeepScreen") ?: true,
            ),
        )
    }

    private fun persist(value: BannerSettings) {
        saved["bnText"] = value.text
        saved["bnSpeed"] = value.speedDpPerSecond
        saved["bnFontSize"] = value.fontSizeSp
        saved["bnDirection"] = value.direction.name
        saved["bnMirror"] = value.mirrored
        saved["bnKeepScreen"] = value.keepScreenOn
    }

    companion object {
        fun factory(applySettings: ApplyBannerSettingsUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    BannerViewModel(applySettings, extras.createSavedStateHandle()) as T
            }
    }
}
