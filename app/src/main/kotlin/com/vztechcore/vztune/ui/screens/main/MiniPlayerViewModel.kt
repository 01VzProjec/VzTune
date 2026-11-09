package com.vztechcore.vztune.ui.screens.main

import androidx.lifecycle.ViewModel
import com.vztechcore.vztune.data.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
) : ViewModel() {

    /** Whether the mini player is collapsed into the compact pill. */
    private val _isMinimized = MutableStateFlow(false)
    val isMinimized: StateFlow<Boolean> = _isMinimized.asStateFlow()

    fun togglePlayPause() { playerController.togglePlayPause() }

    fun next() { playerController.playNext() }

    fun previous() { playerController.playPrevious() }

    fun setMinimized(value: Boolean) {
        if (_isMinimized.value != value) _isMinimized.value = value
    }
}