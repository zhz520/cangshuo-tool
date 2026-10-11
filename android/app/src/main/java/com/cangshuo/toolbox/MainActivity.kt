package com.cangshuo.toolbox

import android.os.Bundle
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cangshuo.toolbox.feature.home.ui.HomeRoute
import com.cangshuo.toolbox.ui.theme.ToolboxTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val container get() = (application as ToolboxApplication).container

    // Dialog/Popup windows are created from the activity context, so pages alone are not
    // enough for the in-app language switch; keep the activity resources localized too.
    private var localizedResources: Resources? = null

    override fun getResources(): Resources = localizedResources ?: super.getResources()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by container.settings.collectAsStateWithLifecycle()
            val accent by container.accent.collectAsStateWithLifecycle()
            val configuration=LocalConfiguration.current
            val localized=remember(settings.language,configuration) {
                val override=Configuration(configuration)
                if(settings.language!="SYSTEM") override.setLocales(LocaleList.forLanguageTags(settings.language))
                val adjusted=this@MainActivity.createConfigurationContext(override)
                object : ContextWrapper(this@MainActivity) {
                    override fun getResources()=adjusted.resources
                }
            }
            LaunchedEffect(localized) {
                localizedResources = localized.resources
                container.updateLocale(localized.resources)
            }
            CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides localized.resources.configuration) {
            ToolboxTheme(darkTheme=when(settings.theme) { "LIGHT"->false; "DARK"->true; else->isSystemInDarkTheme() }, accent=accent) {
                HomeRoute(
                    factory = container.homeViewModelFactory,
                    searchFactory = container.searchViewModelFactory,
                    favoritesFactory = container.favoritesViewModelFactory,
                    recentFactory = container.recentViewModelFactory,
                    authFactory = container.authViewModelFactory,
                    syncFactory = container.syncViewModelFactory,
                    extrasFactory = container.homeExtrasViewModelFactory,
                    feedbackFactory = container.feedbackViewModelFactory,
                    diagnosticsFactory = container.diagnosticsViewModelFactory,
                    gridColumns = settings.gridColumns,
                    settingsFactory = container.settingsViewModelFactory,
                    accentFactory = container.accentViewModelFactory,
                    updateFactory = container.updateViewModelFactory,
                    siteLinkFactory = container.siteLinkViewModelFactory,
                )
            }
            }
        }
    }
}
