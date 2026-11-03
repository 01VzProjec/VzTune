package com.vztechcore.vztune.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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

    val playback = LocalNowPlaying.current
    val miniPlayerVm: MiniPlayerViewModel = hiltViewModel()
    val dominantColor = rememberDominantColor(playback.currentTrack?.thumbnailUrl)

    // ===== ESTADO DE MINIMIZAÇÃO =====
    var isMinimized by rememberSaveable { mutableStateOf(false) }

    // Se entrar na tela Search, já entra minimizado
    LaunchedEffect(backStackEntry?.destination?.route) {
        if (backStackEntry?.destination?.hasRoute<Routes.Search>() == true) {
            isMinimized = true
        }
    }

    // ===== DETECTOR DE SCROLL GLOBAL =====
    // Escuta o scroll de qualquer LazyColumn dentro das telas
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Se rolou para baixo mais de 30px, minimiza
                if (available.y < -30f && !isMinimized) {
                    isMinimized = true
                }
                // Se rolou para cima mais de 30px, expande (opcional, mas boa UX)
                else if (available.y > 30f && isMinimized) {
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
                .nestedScroll(nestedScrollConnection) // Aplica o detector globalmente
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
                    )
                }
            }

            // ===== BARRA INFERIOR =====
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                if (playback.currentTrack != null) {
                    if (isMediaListScreen) {
                        // Em playlists, só o MiniPlayer normal
                        MiniPlayerBar(
                            track = playback.currentTrack,
                            isPlaying = playback.isPlaying,
                            isLoading = playback.isLoading,
                            progress = playback.progress,
                            onPlayPauseClick = miniPlayerVm::togglePlayPause,
                            onNextClick = miniPlayerVm::next,
                            onPrevClick = miniPlayerVm::previous,
                            onClick = { },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        // Fora de playlists, sistema de minimizar
                        AnimatedVisibility(
                            visible = isMinimized,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Círculo da aba
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.18f))
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
                                // Barrinha Compacta
                                CompactMiniPlayerBar(
                                    track = playback.currentTrack,
                                    isPlaying = playback.isPlaying,
                                    onPlayPauseClick = miniPlayerVm::togglePlayPause,
                                    onClick = { },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = !isMinimized,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Column {
                                MiniPlayerBar(
                                    track = playback.currentTrack,
                                    isPlaying = playback.isPlaying,
                                    isLoading = playback.isLoading,
                                    progress = playback.progress,
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
                    }
                } else {
                    // Se não tem música tocando, mostra só a BottomBar
                    if (!isMediaListScreen) {
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

@Composable
private fun Placeholder(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, color = VzOnSurface)
    }
}