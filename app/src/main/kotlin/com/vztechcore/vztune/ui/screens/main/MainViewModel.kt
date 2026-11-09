package com.vztechcore.vztune.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.player.PlaybackState
import com.vztechcore.vztune.core.domain.settings.AppSettings
import com.vztechcore.vztune.data.player.PlaybackRecorder
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.store.AppSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    appSettingsStore: AppSettingsStore,
    private val playerController: PlayerController,
    playbackRecorder: PlaybackRecorder,
) : ViewModel() {

    init {
        playbackRecorder.start(viewModelScope)
    }

    val settings: StateFlow<AppSettings> = appSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val playbackState: StateFlow<PlaybackState> = playerController.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackState())

}
