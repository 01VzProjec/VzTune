package com.vztechcore.vztune.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.ui.components.AppBottomBar
import com.vztechcore.vztune.ui.components.EclipseBackground
import com.vztechcore.vztune.ui.components.LocalNowPlaying
import com.vztechcore.vztune.ui.components.MiniPlayerBar
import com.vztechcore.vztune.ui.components.rememberDominantColor
import com.vztechcore.vztune.ui.screens.main.MiniPlayerViewModel
import com.vztechcore.vztune.ui.screens.search.SearchScreen
import com.vztechcore.vztune.ui.screens.search.SearchViewModel
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

    val playback = LocalNowPlaying.current
    val miniPlayerVm: MiniPlayerViewModel = hiltViewModel()

    // Thumbnail of the first visible hit in the current Search tab.
    // Used as a fallback color source when no track is playing.
    var searchFirstHitThumb by remember { mutableStateOf<String?>(null) }

    val colorSourceUrl = playback.currentTrack?.thumbnailUrl ?: searchFirstHitThumb
    val dominantColor = rememberDominantColor(colorSourceUrl)

    EclipseBackground(baseColor = dominantColor) {
        Box(modifier = modifier.fillMaxSize()) {
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

                    LaunchedEffect(state.hits.firstOrNull(), state.selectedFilter) {
                        searchFirstHitThumb = when (val first = state.hits.firstOrNull()) {
                            is SearchHit.SongHit -> first.track.thumbnailUrl
                            is SearchHit.VideoHit -> first.track.thumbnailUrl
                            is SearchHit.AlbumHit -> first.album.thumbnailUrl
                            is SearchHit.ArtistHit -> first.artist.thumbnailUrl
                            is SearchHit.PlaylistHit -> first.playlist.thumbnailUrl
                            else -> null
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
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                val track = playback.currentTrack
                if (track != null) {
                    MiniPlayerBar(
                        track = track,
                        isPlaying = playback.isPlaying,
                        isLoading = playback.isLoading,
                        onPlayPauseClick = miniPlayerVm::togglePlayPause,
                        onNextClick = miniPlayerVm::next,
                        onPrevClick = miniPlayerVm::previous,
                        onClick = { /* TODO: open Now Playing */ },
                    )
                }

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

@Composable
private fun Placeholder(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, color = VzOnSurface)
    }
}