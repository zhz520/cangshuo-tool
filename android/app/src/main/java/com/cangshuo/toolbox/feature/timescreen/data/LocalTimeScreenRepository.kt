package com.cangshuo.toolbox.feature.timescreen.data

import android.content.res.Resources
import com.cangshuo.toolbox.feature.timescreen.domain.TimeScreenRepository
import java.time.ZonedDateTime
import java.util.Locale

class LocalTimeScreenRepository(private val resources: Resources) : TimeScreenRepository {
    override fun now(): ZonedDateTime = ZonedDateTime.now()

    override fun locale(): Locale = resources.configuration.locales[0]
}
