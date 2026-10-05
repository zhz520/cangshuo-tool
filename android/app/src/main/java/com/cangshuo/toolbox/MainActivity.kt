package com.cangshuo.toolbox

import android.os.Bundle
import android.content.ContextWrapper
import android.content.res.Configuration
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by container.settings.collectAsStateWithLifecycle()
            val configuration=LocalConfiguration.current
            val localized=remember(settings.language,configuration) {
                val override=Configuration(configuration)
                if(settings.language!="SYSTEM") override.setLocales(LocaleList.forLanguageTags(settings.language))
                val adjusted=this@MainActivity.createConfigurationContext(override)
                object : ContextWrapper(this@MainActivity) {
                    override fun getResources()=adjusted.resources
                }
            }
            LaunchedEffect(localized) { container.updateLocale(localized.resources) }
            CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides localized.resources.configuration) {
            ToolboxTheme(darkTheme=when(settings.theme) { "LIGHT"->false; "DARK"->true; else->isSystemInDarkTheme() }) {
                HomeRoute(
                    factory = container.homeViewModelFactory,
                    searchFactory = container.searchViewModelFactory,
                    favoritesFactory = container.favoritesViewModelFactory,
                    recentFactory = container.recentViewModelFactory,
                    authFactory = container.authViewModelFactory,
                    syncFactory = container.syncViewModelFactory,
                    gridColumns = settings.gridColumns,
                )
            }
            }
        }
    }
}
