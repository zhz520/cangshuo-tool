package com.cangshuo.toolbox.feature.banner

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.banner.data.LocalBannerSettingsRepository
import com.cangshuo.toolbox.feature.banner.domain.ApplyBannerSettingsUseCase
import com.cangshuo.toolbox.feature.banner.domain.BannerDirection
import com.cangshuo.toolbox.feature.banner.domain.BannerError
import com.cangshuo.toolbox.feature.banner.domain.BannerPolicy
import com.cangshuo.toolbox.feature.banner.ui.BannerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerViewModelTest {
    private fun model(saved: SavedStateHandle = SavedStateHandle()) = BannerViewModel(
        ApplyBannerSettingsUseCase(LocalBannerSettingsRepository()),
        saved,
    )

    @Test fun emptyStateIsSafeAndBecomesPlayableAsSoonAsTextIsVisible() {
        val viewModel = model()
        assertEquals(BannerError.EMPTY, viewModel.state.value.validation)
        assertEquals("", viewModel.state.value.settings.text)
        viewModel.setText(" Hello ")
        assertNull(viewModel.state.value.validation)
        assertEquals(" Hello ", viewModel.state.value.settings.text)
        viewModel.setText("   ")
        assertEquals(BannerError.EMPTY, viewModel.state.value.validation)
    }

    @Test fun settingsAreSanitizedBoundedAndPersisted() {
        val saved = SavedStateHandle()
        val viewModel = model(saved)
        viewModel.setText("line 1\n😀".repeat(30))
        viewModel.setSpeed(999f)
        viewModel.setFontSize(1f)
        viewModel.setDirection(BannerDirection.LEFT_TO_RIGHT)
        viewModel.setMirrored(true)
        viewModel.setKeepScreenOn(false)
        val settings = viewModel.state.value.settings
        assertEquals(BannerPolicy.MAX_TEXT_CODE_POINTS, settings.text.codePointCount(0, settings.text.length))
        assertEquals(BannerPolicy.MAX_SPEED, settings.speedDpPerSecond)
        assertEquals(BannerPolicy.MIN_FONT_SIZE, settings.fontSizeSp)
        assertEquals(BannerDirection.LEFT_TO_RIGHT, settings.direction)
        assertTrue(settings.mirrored)
        assertFalse(settings.keepScreenOn)
        assertEquals("LEFT_TO_RIGHT", saved.get<String>("bnDirection"))
        assertEquals(false, saved.get<Boolean>("bnKeepScreen"))
    }

    @Test fun resetStylePreservesTextAndClearsOnlyAppearanceChoices() {
        val viewModel = model()
        viewModel.setText("Important")
        viewModel.setSpeed(BannerPolicy.MAX_SPEED)
        viewModel.setFontSize(BannerPolicy.MIN_FONT_SIZE)
        viewModel.setDirection(BannerDirection.LEFT_TO_RIGHT)
        viewModel.setMirrored(true)
        viewModel.setKeepScreenOn(false)
        viewModel.resetStyle()
        val settings = viewModel.state.value.settings
        assertEquals("Important", settings.text)
        assertEquals(88f, settings.speedDpPerSecond)
        assertEquals(72f, settings.fontSizeSp)
        assertEquals(BannerDirection.RIGHT_TO_LEFT, settings.direction)
        assertFalse(settings.mirrored)
        assertTrue(settings.keepScreenOn)
    }

    @Test fun clearRemovesOnlyText() {
        val viewModel = model()
        viewModel.setText("Hello")
        viewModel.setSpeed(120f)
        viewModel.setMirrored(true)
        viewModel.clearText()
        assertEquals("", viewModel.state.value.settings.text)
        assertEquals(120f, viewModel.state.value.settings.speedDpPerSecond)
        assertTrue(viewModel.state.value.settings.mirrored)
    }

    @Test fun savedStateRestoresEveryBoundedOptionAndRejectsInvalidEnum() {
        val saved = SavedStateHandle(
            mapOf(
                "bnText" to " 欢迎 ",
                "bnSpeed" to 333f,
                "bnFontSize" to 999f,
                "bnDirection" to "BROKEN",
                "bnMirror" to true,
                "bnKeepScreen" to false,
            ),
        )
        val state = model(saved).state.value
        assertEquals(" 欢迎 ", state.settings.text)
        assertEquals(BannerPolicy.MAX_SPEED, state.settings.speedDpPerSecond)
        assertEquals(BannerPolicy.MAX_FONT_SIZE, state.settings.fontSizeSp)
        assertEquals(BannerDirection.RIGHT_TO_LEFT, state.settings.direction)
        assertTrue(state.settings.mirrored)
        assertFalse(state.settings.keepScreenOn)
        assertNull(state.validation)
    }
}
