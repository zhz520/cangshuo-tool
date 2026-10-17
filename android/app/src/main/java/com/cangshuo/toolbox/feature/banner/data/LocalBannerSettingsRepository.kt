package com.cangshuo.toolbox.feature.banner.data

import com.cangshuo.toolbox.feature.banner.domain.BannerError
import com.cangshuo.toolbox.feature.banner.domain.BannerPolicy
import com.cangshuo.toolbox.feature.banner.domain.BannerSettings
import com.cangshuo.toolbox.feature.banner.domain.BannerSettingsRepository

class LocalBannerSettingsRepository : BannerSettingsRepository {
    override fun sanitizeInput(raw: String): String = BannerPolicy.sanitizeInput(raw)

    override fun validate(settings: BannerSettings): BannerError? = BannerPolicy.validate(settings)

    override fun visibleCopyPositions(
        startX: Float,
        cycleWidth: Float,
        textWidth: Float,
        viewportWidth: Float,
    ): List<Float> = BannerPolicy.visibleCopyPositions(startX, cycleWidth, textWidth, viewportWidth)
}
