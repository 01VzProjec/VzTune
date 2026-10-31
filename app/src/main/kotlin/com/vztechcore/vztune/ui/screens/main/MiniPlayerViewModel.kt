package com.vztechcore.vztune.ui.screens.main

import androidx.lifecycle.ViewModel
import com.vztechcore.vztune.data.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
) : ViewModel() {

    fun togglePlayPause() = playerController.togglePlayPause()
    fun next() = playerController.next()
    fun previous() = playerController.previous()
}