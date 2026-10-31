package com.vztechcore.vztune.ui.components

import androidx.compose.runtime.compositionLocalOf
import com.vztechcore.vztune.core.domain.player.PlaybackState

/**
 * Provides the current playback state to every composable that needs it.
 * Screens read it via `LocalNowPlaying.current`.
 * Value is provided once at the top of MainActivity from MainViewModel.
 */
val LocalNowPlaying = compositionLocalOf { PlaybackState() }