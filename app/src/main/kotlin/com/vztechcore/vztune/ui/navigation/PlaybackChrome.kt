package com.vztechcore.vztune.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.player.PlaybackState
import com.vztechcore.vztune.ui.components.feedback.DownloadStartedBanner
import com.vztechcore.vztune.ui.components.layout.AppBottomBar
import com.vztechcore.vztune.ui.components.player.CompactMiniPlayerBar
import com.vztechcore.vztune.ui.components.player.MiniPlayerBar
import com.vztechcore.vztune.ui.screens.main.MiniPlayerViewModel

/**
 * Bottom chrome of the app: download banner, mini player (expanded or compact) and bottom bar.
 * Rendered from [AppNavHost] inside the root Box, hence the [BoxScope].
 */
@Composable
fun BoxScope.PlaybackChrome(
    navController: NavController,
    currentTab: AppTab,
    playback: PlaybackState,
    track: Track?,
    dominantColor: Color,
    miniPlayerVm: MiniPlayerViewModel,
    isMinimized: Boolean,
    isDetailScreen: Boolean,
    isTrackInfoScreen: Boolean,
    bannerTrack: Track? = null,
    onDismissBanner: () -> Unit = {},
    onViewDownloadsClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        DownloadStartedBanner(
            track = bannerTrack,
            onDismiss = onDismissBanner,
            onViewClick = onViewDownloadsClick,
        )

        if (!isTrackInfoScreen) {
            when {
                track != null && isDetailScreen -> {
                    MiniPlayerBar(
                        track = track,
                        isPlaying = playback.isPlaying,
                        isLoading = playback.isLoading,
                        progress = playback.progress,
                        dominantColor = dominantColor,
                        onPlayPauseClick = miniPlayerVm::togglePlayPause,
                        onNextClick = miniPlayerVm::next,
                        onPrevClick = miniPlayerVm::previous,
                        onClick = { },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                track != null -> {
                    AnimatedVisibility(
                        visible = isMinimized,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        ) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(),
                        modifier = Modifier
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .clickable { miniPlayerVm.setMinimized(false) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = currentTab.selectedIcon,
                                    contentDescription = "Abrir Menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            CompactMiniPlayerBar(
                                track = track,
                                isPlaying = playback.isPlaying,
                                isLoading = playback.isLoading,
                                progress = playback.progress,
                                dominantColor = dominantColor,
                                onPlayPauseClick = miniPlayerVm::togglePlayPause,
                                onNextClick = miniPlayerVm::next,
                                onClick = { },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = !isMinimized,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        ) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(),
                    ) {
                        Column {
                            MiniPlayerBar(
                                track = track,
                                isPlaying = playback.isPlaying,
                                isLoading = playback.isLoading,
                                progress = playback.progress,
                                dominantColor = dominantColor,
                                onPlayPauseClick = miniPlayerVm::togglePlayPause,
                                onNextClick = miniPlayerVm::next,
                                onPrevClick = miniPlayerVm::previous,
                                onClick = { },
                            )
                            AppBottomBar(
                                selected = currentTab,
                                onSelect = { navController.navigateToTab(it) },
                            )
                        }
                    }
                }

                !isDetailScreen -> {
                    AppBottomBar(
                        selected = currentTab,
                        onSelect = { navController.navigateToTab(it) },
                    )
                }
            }
        }
    }
}