package com.vztechcore.vztune.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.feedback.EclipseBackground
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.feedback.rememberDominantColor
import com.vztechcore.vztune.ui.screens.artist.ArtistScreen
import com.vztechcore.vztune.ui.screens.artist.ArtistViewModel
import com.vztechcore.vztune.ui.screens.artist.list.ArtistListScreen
import com.vztechcore.vztune.ui.screens.artist.list.ArtistListViewModel
import com.vztechcore.vztune.ui.screens.downloads.DownloadBannerViewModel
import com.vztechcore.vztune.ui.screens.downloads.DownloadsScreen
import com.vztechcore.vztune.ui.screens.downloads.DownloadsViewModel
import com.vztechcore.vztune.ui.screens.library.LibraryScreen
import com.vztechcore.vztune.ui.screens.library.LibraryViewModel
import com.vztechcore.vztune.ui.screens.library.list.LibraryListScreen
import com.vztechcore.vztune.ui.screens.library.list.LibraryListViewModel
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
    val isArtistScreen = backStackEntry?.destination?.hasRoute<Routes.Artist>() == true
    val isArtistListScreen = backStackEntry?.destination?.hasRoute<Routes.ArtistList>() == true
    val isLibraryListScreen = backStackEntry?.destination?.hasRoute<Routes.LibraryList>() == true
    val isDownloadsScreen = backStackEntry?.destination?.hasRoute<Routes.Downloads>() == true
    val isDetailScreen = isMediaListScreen || isArtistScreen || isArtistListScreen || isLibraryListScreen || isDownloadsScreen
    val isTrackInfoScreen = backStackEntry?.destination?.hasRoute<Routes.TrackInfo>() == true

    val playback = LocalNowPlaying.current
    val track = playback.currentTrack

    val miniPlayerVm: MiniPlayerViewModel = hiltViewModel()
    val downloadBannerVm: DownloadBannerViewModel = hiltViewModel()
    val dominantColor = rememberDominantColor(track?.thumbnailUrl)
    val isMinimized by miniPlayerVm.isMinimized.collectAsStateWithLifecycle()
    val bannerTrack by downloadBannerVm.bannerTrack.collectAsStateWithLifecycle()

    // Minimize the player when the user lands on the Search tab
    LaunchedEffect(backStackEntry?.destination?.route) {
        if (backStackEntry?.destination?.hasRoute<Routes.Search>() == true) {
            miniPlayerVm.setMinimized(true)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -30f) miniPlayerVm.setMinimized(true)
                else if (available.y > 30f) miniPlayerVm.setMinimized(false)
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

                composable<Routes.Downloads> {
                    val vm: DownloadsViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    DownloadsScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onPlayTrack = vm::onPlayTrack,
                        onCancelDownload = vm::onCancelDownload,
                        onRetryDownload = vm::onRetryDownload,
                        onDismissFailed = vm::onDismissFailed,
                        onDeleteDownloaded = vm::onDeleteDownloaded,
                    )
                }

                composable<Routes.Library> {
                    val vm: LibraryViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LibraryScreen(
                        state = state,
                        onTabSelected = vm::onTabSelected,
                        onShortcutClick = { navController.navigate(Routes.LibraryList(it.name)) },
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { navController.navigateToTrackInfo(it) },
                        onPlaylistClick = { item ->
                            navController.navigate(
                                Routes.MediaList(
                                    id = item.id,
                                    isAlbum = false,
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    thumbnailUrl = item.imageUrl,
                                )
                            )
                        },
                        onCreatePlaylist = vm::createPlaylist,
                    )
                }

                composable<Routes.LibraryList> {
                    val vm: LibraryListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LibraryListScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { navController.navigateToTrackInfo(it) },
                        onArtistClick = { artist ->
                            navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl))
                        },
                        onShuffleClick = vm::onShuffleClick,
                        onPlayAllClick = vm::onPlayAllClick,
                    )
                }

                composable<Routes.Search> {
                    val vm: SearchViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    val favoriteKeys by vm.favoriteKeys.collectAsStateWithLifecycle()
                    val downloadedKeys by vm.downloadedKeys.collectAsStateWithLifecycle()
                    val localPlaylists by vm.localPlaylists.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        vm.navigationEvent.collect { event ->
                            when (event) {
                                is SearchViewModel.NavigationEvent.OpenPlaylist -> navController.navigate(
                                    Routes.MediaList(
                                        id = event.id,
                                        isAlbum = false,
                                        title = event.title,
                                        subtitle = event.owner,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                                is SearchViewModel.NavigationEvent.OpenArtist -> navController.navigate(
                                    Routes.Artist(
                                        id = event.id,
                                        name = event.name,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                                is SearchViewModel.NavigationEvent.OpenAlbum -> navController.navigate(
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

                    SearchScreen(
                        state = state,
                        favoriteKeys = favoriteKeys,
                        downloadedKeys = downloadedKeys,
                        localPlaylists = localPlaylists,
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
                        onAddToQueueClick = vm::onAddToQueueClick,
                        onToggleFavorite = vm::onToggleFavorite,
                        onAddToPlaylist = vm::onAddToPlaylist,
                        onDownloadClick = vm::onDownloadClick,
                        onOpenArtistByName = vm::onOpenArtistByName,
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
                        onFavoriteClick = vm::onFavoriteClick,
                    )
                }

                composable<Routes.Artist> {
                    val vm: ArtistViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    ArtistScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onRandomClick = vm::onRandomClick,
                        onFollowClick = vm::onFollowClick,
                        onSongClick = vm::onSongClick,
                        onVideoClick = vm::onVideoClick,
                        onTrackInfoClick = { navController.navigateToTrackInfo(it) },
                        onAlbumClick = { navController.openAlbum(it) },
                        onPlaylistClick = { navController.openPlaylist(it) },
                        onArtistClick = { artist ->
                            navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl))
                        },
                        onSeeAllClick = { kind, ref ->
                            navController.navigate(
                                Routes.ArtistList(
                                    artistId = state.artistId,
                                    artistName = state.name,
                                    kind = kind.name,
                                    browseId = ref.browseId,
                                    params = ref.params,
                                )
                            )
                        },
                    )
                }

                composable<Routes.ArtistList> {
                    val vm: ArtistListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    ArtistListScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onLoadMore = vm::onLoadMore,
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { navController.navigateToTrackInfo(it) },
                        onAlbumClick = { navController.openAlbum(it) },
                        onPlaylistClick = { navController.openPlaylist(it) },
                    )
                }

                composable<Routes.TrackInfo> {
                    val vm: TrackInfoViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    val isDownloaded by vm.isDownloaded.collectAsStateWithLifecycle()
                    TrackInfoScreen(
                        state = state,
                        isDownloaded = isDownloaded,
                        onBackClick = { navController.popBackStack() },
                        onFavoriteClick = vm::onFavoriteClick,
                        onAddToPlaylist = vm::onAddToPlaylist,
                        onDownloadClick = vm::onDownloadClick,
                    )
                }
            }

            PlaybackChrome(
                navController = navController,
                currentTab = currentTab,
                playback = playback,
                track = track,
                dominantColor = dominantColor,
                miniPlayerVm = miniPlayerVm,
                isMinimized = isMinimized,
                isDetailScreen = isDetailScreen,
                isTrackInfoScreen = isTrackInfoScreen,
                bannerTrack = bannerTrack,
                onDismissBanner = downloadBannerVm::dismissBanner,
                onViewDownloadsClick = {
                    if (!isDownloadsScreen) {
                        navController.navigate(Routes.Downloads)
                    }
                },
            )
        }
    }
}

@Composable
private fun Placeholder(message: String) {
    Box(Modifier.fillMaxSize()) {
        Text(text = message, color = VzOnSurface, modifier = Modifier.align(Alignment.Center))
        AppCredits(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}