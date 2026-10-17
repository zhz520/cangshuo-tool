package com.cangshuo.toolbox.feature.banner.ui

import com.cangshuo.toolbox.feature.banner.domain.BannerError
import com.cangshuo.toolbox.feature.banner.domain.BannerSettings

data class BannerUiState(
    val settings: BannerSettings = BannerSettings(),
    val validation: BannerError? = BannerError.EMPTY,
)
