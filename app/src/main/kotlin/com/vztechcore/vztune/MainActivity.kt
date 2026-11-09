package com.vztechcore.vztune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.navigation.AppNavHost
import com.vztechcore.vztune.ui.screens.main.MainViewModel
import com.vztechcore.vztune.ui.theme.VzTuneTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = hiltViewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val playbackState by vm.playbackState.collectAsStateWithLifecycle()

            VzTuneTheme(themeMode = settings.themeMode) {
                CompositionLocalProvider(LocalNowPlaying provides playbackState) {
                    AppNavHost()
                }
            }
        }
    }
}