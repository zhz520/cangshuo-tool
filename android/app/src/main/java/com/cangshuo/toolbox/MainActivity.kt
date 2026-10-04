package com.cangshuo.toolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cangshuo.toolbox.feature.home.ui.HomeRoute
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as ToolboxApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToolboxTheme {
                HomeRoute(
                    factory = container.homeViewModelFactory,
                    searchFactory = container.searchViewModelFactory,
                    favoritesFactory = container.favoritesViewModelFactory,
                )
            }
        }
    }
}
