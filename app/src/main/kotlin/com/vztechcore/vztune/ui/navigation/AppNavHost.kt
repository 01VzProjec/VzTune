package com.vztechcore.vztune.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.player.PlaybackChrome
import com.vztechcore.vztune.ui.components.trackmenu.TrackMenuContext
import com.vztechcore.vztune.ui.components.trackmenu.TrackMenuViewModel
import com.vztechcore.vztune.ui.screens.artist.ArtistListScreen
import com.vztechcore.vztune.ui.screens.artist.ArtistListViewModel
import com.vztechcore.vztune.ui.screens.artist.ArtistScreen
import com.vztechcore.vztune.ui.screens.artist.ArtistViewModel
import com.vztechcore.vztune.ui.screens.downloads.DownloadsScreen
import com.vztechcore.vztune.ui.screens.downloads.DownloadsViewModel
import com.vztechcore.vztune.ui.screens.home.HomeScreen
import com.vztechcore.vztune.ui.screens.home.HomeViewModel
import com.vztechcore.vztune.ui.screens.library.LibraryListScreen
import com.vztechcore.vztune.ui.screens.library.LibraryListViewModel
import com.vztechcore.vztune.ui.screens.library.LibraryScreen
import com.vztechcore.vztune.ui.screens.library.LibraryViewModel
import com.vztechcore.vztune.ui.screens.medialist.MediaListScreen
import com.vztechcore.vztune.ui.screens.medialist.MediaListViewModel
import com.vztechcore.vztune.ui.screens.search.SearchScreen
import com.vztechcore.vztune.ui.screens.search.SearchViewModel
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoScreen
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoViewModel
import com.vztechcore.vztune.ui.theme.VzOnSurface
import com.vztechcore.vztune.ui.viewmodel.AnalyticsViewModel
import com.vztechcore.vztune.ui.viewmodel.BannerViewModel
import com.vztechcore.vztune.ui.viewmodel.MiniPlayerViewModel

/**
 * Main navigation host.
 *
 * It wires every screen and forwards navigation events.
 *
 * Fix for album-in-album navigation: the MediaList screen now receives
 * [onAlbumClick] and [onOpenArtist]. Without them the footer click fell into
 * the default empty lambda and nothing happened. Now tapping an album inside
 * an album opens that album, and tapping the artist opens the artist.
 *
 * All UI text is in Portuguese. Code and comments are in English.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    currentTab: AppTab,
    playback: PlaybackState,
    track: Track?,
    dominantColor: Color,
    miniPlayerVm: MiniPlayerViewModel,
    isMinimized: Boolean,
    isDetailScreen: Boolean,
    isTrackInfoScreen: Boolean,
    bannerTrack: Track?,
    bannerVm: BannerViewModel,
    menuVm: TrackMenuViewModel,
    favoriteKeys: Set<String>,
    downloadedKeys: Set<String>,
    activeDownloads: Map<String, com.vztechcore.vztune.ui.screens.downloads.DownloadItem>,
    localPlaylists: List<LocalPlaylistUi>,
    resolvedAlbum: Album?,
    modifier: Modifier = Modifier,
) {
    // Opens the track menu for a track with the given context.
    fun openTrackMenu(
        targetTrack: Track,
        context: TrackMenuContext,
        localPlaylistId: String? = null,
    ) {
        menuVm.open(targetTrack, context, localPlaylistId)
    }

    LaunchedEffect(Unit) {
        menuVm.navigationEvent.collect { event ->
            when (event) {
                is TrackMenuViewModel.MenuNavigationEvent.OpenAlbum -> navController.openAlbum(event.album)
                is TrackMenuViewModel.MenuNavigationEvent.OpenArtist -> navController.navigate(
                    Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)
                )
                is TrackMenuViewModel.MenuNavigationEvent.OpenTrackInfo -> navController.navigateToTrackInfo(event.track)
                is TrackMenuViewModel.MenuNavigationEvent.OpenPlaylist -> navController.openPlaylist(event.playlist)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<Routes.Home> {
                val vm: HomeViewModel = hiltViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    favoriteKeys = favoriteKeys,
                    downloadedKeys = downloadedKeys,
                    activeDownloads = activeDownloads,
                    onTrackClick = vm::onTrackClick,
                    onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                    onAlbumClick = { album -> navController.openAlbum(album) },
                    onPlaylistClick = { playlist -> navController.openPlaylist(playlist) },
                    onArtistClick = { artist -> navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl)) },
                    onOpenAlbum = { album -> navController.openAlbum(album) },
                    onOpenArtistByName = vm::onOpenArtistByName,
                )
            }

            composable<Routes.Analytics> {
                val vm: AnalyticsViewModel = hiltViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                Placeholder("Analytics em breve")
            }

            composable<Routes.Library> {
                val vm: LibraryViewModel = hiltViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                LibraryScreen(
                    state = state,
                    onTabSelected = vm::onTabSelected,
                    onShortcutClick = { shortcut ->
                        navController.navigate(Routes.LibraryList(shortcut.name))
                    },
                    onTrackClick = vm::onTrackClick,
                    onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
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
                    onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                    onArtistClick = { artist ->
                        navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl))
                    },
                    onShuffleClick = vm::onShuffleClick,
                    onPlayAllClick = vm::onPlayAllClick,
                    onDownloadsHistoryClick = { navController.navigate(Routes.Downloads) },
                )
            }

            composable<Routes.Downloads> {
                val vm: DownloadsViewModel = hiltViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                DownloadsScreen(
                    state = state,
                    onBackClick = { navController.popBackStack() },
                    onTabSelected = vm::onTabSelected,
                    onTogglePauseResume = vm::onTogglePauseResume,
                    onCancelDownload = vm::onCancelDownload,
                    onDeleteDownload = vm::onDeleteDownload,
                    onPlayTrack = vm::onPlayTrack,
                    onPauseAll = vm::onPauseAll,
                    onResumeAll = vm::onResumeAll,
                    onCancelAll = vm::onCancelAll,
                )
            }

            composable<Routes.Search> {
                val vm: SearchViewModel = hiltViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()

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
                    activeDownloads = activeDownloads,
                    localPlaylists = localPlaylists,
                    resolvedAlbum = resolvedAlbum,
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
                    onDownloadTrack = vm::onDownloadTrack,
                    onRemoveDownload = vm::onRemoveDownload,
                    onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
                    onPrepareTrackMenu = menuVm::resolveAlbumFor,
                    onOpenAlbum = { album -> navController.openAlbum(album) },
                    onOpenArtistByName = vm::onOpenArtistByName,
                    onTrackInfoClick = { navController.navigateToTrackInfo(it) },
                )
            }

            composable<Routes.MediaList> {
                val vm: MediaListViewModel = hiltViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                val playlistId = state.listId
                val menuCtx = when {
                    state.isAlbum -> TrackMenuContext.DEFAULT
                    state.isLocal || isLocalPlaylistId(playlistId) -> TrackMenuContext.LOCAL_PLAYLIST
                    else -> TrackMenuContext.EXTERNAL_PLAYLIST
                }

                LaunchedEffect(Unit) {
                    vm.navigationEvent.collect { event ->
                        when (event) {
                            is MediaListViewModel.NavigationEvent.OpenArtist -> navController.navigate(
                                Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)
                            )
                            MediaListViewModel.NavigationEvent.CloseScreen -> navController.popBackStack()
                        }
                    }
                }

                MediaListScreen(
                    state = state,
                    activeDownloads = activeDownloads,
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
                    onTrackInfoClick = { t ->
                        openTrackMenu(
                            targetTrack = t,
                            context = menuCtx,
                            localPlaylistId = playlistId.takeIf { menuCtx == TrackMenuContext.LOCAL_PLAYLIST },
                        )
                    },
                    onFavoriteClick = vm::onFavoriteClick,
                    onPlayNextAll = vm::onPlayNextAll,
                    onAddToQueueAll = vm::onAddToQueueAll,
                    onDownloadAll = vm::onDownloadAll,
                    onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
                    onOpenArtist = vm::onOpenArtist,
                    onOpenArtistByName = vm::onOpenArtistByName,
                    onDeleteLocalPlaylist = vm::onDeleteLocalPlaylist,
                    onRenameLocalPlaylist = vm::onRenameLocalPlaylist,
                    onChangeLocalPlaylistCover = vm::onChangeLocalPlaylistCover,
                    onChangeCoverColor = vm::onChangeCoverColor,
                    // FIX: album click inside an album now navigates.
                    // Without this the footer click fell into the default empty lambda.
                    onAlbumClick = { album ->
                        if (album.id != state.listId) navController.openAlbum(album)
                    },
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
                    onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
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
                    onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                    onAlbumClick = { navController.openAlbum(it) },
                    onPlaylistClick = { navController.openPlaylist(it) },
                )
            }

            composable<Routes.TrackInfo> {
                val vm: TrackInfoViewModel = hiltViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                TrackInfoScreen(
                    state = state,
                    isDownloaded = state.isDownloaded,
                    onBackClick = { navController.popBackStack() },
                    onFavoriteClick = vm::onFavoriteClick,
                    onAddToPlaylist = vm::onAddToPlaylist,
                    onDownloadClick = {
                        if (state.isDownloaded) vm.onRemoveDownloadClick() else vm.onDownloadClick()
                    },
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
            onDismissBanner = bannerVm::dismissBanner,
            onViewDownloadsClick = { navController.navigate(Routes.Downloads) },
        )
    }
}

/** Simple placeholder used for screens not yet implemented. */
@Composable
private fun Placeholder(message: String) {
    Box(Modifier.fillMaxSize()) {
        Text(text = message, color = VzOnSurface, modifier = Modifier.align(Alignment.Center))
        AppCredits(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}
