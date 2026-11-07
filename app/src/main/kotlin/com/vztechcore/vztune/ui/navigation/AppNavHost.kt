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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppBottomBar
import com.vztechcore.vztune.ui.components.CompactMiniPlayerBar
import com.vztechcore.vztune.ui.components.EclipseBackground
import com.vztechcore.vztune.ui.components.LocalNowPlaying
import com.vztechcore.vztune.ui.components.MiniPlayerBar
import com.vztechcore.vztune.ui.components.rememberDominantColor
import com.vztechcore.vztune.ui.screens.main.MiniPlayerViewModel
import com.vztechcore.vztune.ui.screens.medialist.MediaListScreen
import com.vztechcore.vztune.ui.screens.medialist.MediaListViewModel
import com.vztechcore.vztune.ui.screens.search.SearchScreen
import com.vztechcore.vztune.ui.screens.search.SearchViewModel
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoScreen
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoViewModel
import com.vztechcore.vztune.ui.theme.VzOnSurface

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val currentTab = when {
        backStackEntry?.destination?.hasRoute<Routes.Search>() == true -> AppTab.SEARCH
        backStackEntry?.destination?.hasRoute<Routes.Analytics>() == true -> AppTab.ANALYTICS
        backStackEntry?.destination?.hasRoute<Routes.Library>() == true -> AppTab.LIBRARY
        else -> AppTab.HOME
    }

    val isMediaListScreen = backStackEntry?.destination?.hasRoute<Routes.MediaList>() == true
    val isTrackInfoScreen = backStackEntry?.destination?.hasRoute<Routes.TrackInfo>() == true
    val isFullScreenRoute = isMediaListScreen || isTrackInfoScreen

    val playback = LocalNowPlaying.current
    val track = playback.currentTrack

    val miniPlayerVm: MiniPlayerViewModel = hiltViewModel()
    val dominantColor = rememberDominantColor(track?.thumbnailUrl)

    var isMinimized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(backStackEntry?.destination?.route) {
        if (backStackEntry?.destination?.hasRoute<Routes.Search>() == true) {
            isMinimized = true
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -30f && !isMinimized) {
                    isMinimized = true
                } else if (available.y > 30f && isMinimized) {
                    isMinimized = false
                }
                return Offset.Zero
            }
        }
    }

    EclipseBackground(baseColor = dominantColor) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
            ) {
                composable<Routes.Home> { Placeholder("Home — coming soon") }
                composable<Routes.Analytics> { Placeholder("Analytics — coming soon") }
                composable<Routes.Library> { Placeholder("Library — coming soon") }

                composable<Routes.Search> {
                    val vm: SearchViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        vm.navigationEvent.collect { event ->
                            when (event) {
                                is SearchViewModel.NavigationEvent.OpenPlaylist -> {
                                    navController.navigate(
                                        Routes.MediaList(
                                            id = event.id,
                                            isAlbum = false,
                                            title = event.title,
                                            subtitle = event.owner,
                                            thumbnailUrl = event.thumbnailUrl,
                                        )
                                    )
                                }
                                is SearchViewModel.NavigationEvent.OpenAlbum -> {
                                    navController.navigate(
                                        Routes.MediaList(
                                            id = event.id,
                                            isAlbum = true,
                                            title = event.title,
                                            subtitle = event.artist,
                                            thumbnailUrl = event.thumbnailUrl,
                                        )
                                    )
                                }
                            }
                        }
                    }

                    SearchScreen(
                        state = state,
                        onQueryChange = vm::onQueryChange,
                        onFocusChanged = vm::onFocusChanged,
                        onFilterSelect = vm::onFilterSelect,
                        onSubmit = vm::onSubmit,
                        onRefresh = vm::onRefresh,
                        onRemoveHistory = vm::onRemoveHistory,
                        onClearHistory = vm::onClearHistory,
                        onLoadMore = vm::onLoadMore,
                        onHitClick = vm::onHitClick,
                        onPlayNextClick = vm::onPlayNextClick,
                        onTrackInfoClick = { navController.navigateToTrackInfo(it) },
                    )
                }

                composable<Routes.MediaList> {
                    val vm: MediaListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    MediaListScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onTabSelected = vm::onTabSelected,
                        onTrackClick = vm::onTrackClick,
                        onPlayAllClick = vm::onPlayAll,
                        onShuffleClick = vm::onShuffle,
                        onSortSelected = vm::onSortOrderSelected,
                        onReorder = vm::onReorder,
                        onStartShuffle = vm::startShuffle,
                        onShuffleOnce = vm::shuffleOnce,
                        onStopShuffle = vm::stopShuffle,
                        onPlayNextClick = vm::onPlayNextClick,
                        onTrackInfoClick = { navController.navigateToTrackInfo(it) },
                    )
                }

                composable<Routes.TrackInfo> {
                    val vm: TrackInfoViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    TrackInfoScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                    )
                }
            }

            // Bottom player chrome — hidden while a full-screen route is on top
            if (!isFullScreenRoute) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    if (track != null) {
                        AnimatedVisibility(
                            visible = isMinimized,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            ) + fadeIn(),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .clickable { isMinimized = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = currentTab.selectedIcon,
                                        contentDescription = "Abrir Menu",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                CompactMiniPlayerBar(
                                    track = track,
                                    isPlaying = playback.isPlaying,
                                    isLoading = playback.isLoading,
                                    onPlayPauseClick = miniPlayerVm::togglePlayPause,
                                    onNextClick = miniPlayerVm::next,
                                    onClick = { },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = !isMinimized,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            ) + fadeIn(),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter)
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
                                    onClick = { }
                                )
                                AppBottomBar(
                                    selected = currentTab,
                                    onSelect = { tab ->
                                        val route = when (tab) {
                                            AppTab.HOME -> Routes.Home
                                            AppTab.ANALYTICS -> Routes.Analytics
                                            AppTab.LIBRARY -> Routes.Library
                                            AppTab.SEARCH -> Routes.Search
                                        }
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }
                    } else {
                        AppBottomBar(
                            selected = currentTab,
                            onSelect = { tab ->
                                val route = when (tab) {
                                    AppTab.HOME -> Routes.Home
                                    AppTab.ANALYTICS -> Routes.Analytics
                                    AppTab.LIBRARY -> Routes.Library
                                    AppTab.SEARCH -> Routes.Search
                                }
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Converts a domain [Track] into the serializable [Routes.TrackInfo] payload
 * and pushes it onto the back stack.
 *
 * Note: `streamUrl` is intentionally not passed — TrackInfoViewModel re-resolves
 * the stream via MusicRepository to get fresh itag/mime/quality.
 */
private fun NavController.navigateToTrackInfo(track: Track) {
    navigate(
        Routes.TrackInfo(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            thumbnailUrl = track.thumbnailUrl,
            sourceName = track.source.name,
            durationSeconds = track.durationSeconds,
        )
    )
}

@Composable
private fun Placeholder(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, color = VzOnSurface)
    }
}