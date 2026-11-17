#!/bin/bash
set -e

# VzTune - Animacao de entrada NA PRÓPRIA TELA (ScreenSlideIn)
# Rode na raiz do projeto VzTune.
# A tela desliza da direita (detalhe / aba à direita) ou da esquerda
# (aba à esquerda) junto com o conteúdo — por isso agora anima até
# nas telas independentes (Search, playlist, álbum, artista, Library).
# Voltar continua no NavHost: detalhe sai p/ direita, aba inverte.

if [ ! -f "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt" ]; then
  echo "ERRO: rode este script na raiz do projeto VzTune."
  exit 1
fi

# --- 1. app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/ScreenSlideIn.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/ScreenSlideIn.kt" <<'EOF'
package com.vztechcore.vztune.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/**
 * Animação de entrada da própria tela — suave e rápida.
 *
 * A tela desliza da direita (detalhe ou aba à direita) ou da esquerda
 * (aba à esquerda) + fade. Roda só na primeira composição da tela.
 *
 * Ao voltar (pop), a tela que volta já estava composta, então não anima
 * de novo — a tela que sai usa a popExit do NavHost (sai p/ direita).
 *
 * Por que na tela e não só no NavHost? Telas independentes (Search,
 * playlist, álbum, artista, Library...) carregam dado assíncrono do
 * ViewModel, então o slide do NavHost rolava antes do conteúdo existir
 * e parecia que não animava. Animando na tela, o slide acontece junto
 * com o conteúdo.
 */
@Composable
fun ScreenSlideIn(
    fromLeft: Boolean = false,
    content: @Composable () -> Unit,
) {
    // -1f = vem da esquerda (fora da tela), 1f = vem da direita.
    val offset = remember { Animatable(if (fromLeft) -1f else 1f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { offset.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
        launch { alpha.animateTo(1f, tween(200)) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offset.value * size.width
                this.alpha = alpha.value
            },
    ) {
        content()
    }
}
EOF

# --- 2. app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/NavTransitions.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/NavTransitions.kt" <<'EOF'
package com.vztechcore.vztune.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/**
 * Transições de VOLTAR (pop) do VzTune — suaves e rápidas.
 * A entrada p/ frente é feita pela própria tela (ScreenSlideIn).
 *
 * - Detalhe (playlist, álbum, artista, ver tudo, downloads, info da faixa):
 *   ao voltar, sai pra direita revelando a tela anterior.
 * - Abas (Home, Analytics, Library, Search): a direção segue a ordem das abas.
 *   Voltando p/ uma aba à esquerda, a tela atual sai p/ direita; voltando
 *   p/ uma aba à direita, sai p/ esquerda.
 */

private const val SLIDE_IN_MS = 300
private const val SLIDE_OUT_MS = 280
private const val FADE_MS = 200

/** Índice da aba na barra de baixo, da esquerda p/ direita. */
private fun NavDestination.tabIndex(): Int? = when {
    hasRoute<Routes.Home>() -> 0
    hasRoute<Routes.Analytics>() -> 1
    hasRoute<Routes.Library>() -> 2
    hasRoute<Routes.Search>() -> 3
    else -> null
}

private fun slideInFromRight(): EnterTransition =
    slideInHorizontally(tween(SLIDE_IN_MS, easing = FastOutSlowInEasing)) { it } +
        fadeIn(tween(FADE_MS))

private fun slideInFromLeft(): EnterTransition =
    slideInHorizontally(tween(SLIDE_IN_MS, easing = FastOutSlowInEasing)) { -it } +
        fadeIn(tween(FADE_MS))

private fun slideOutToLeft(): ExitTransition =
    slideOutHorizontally(tween(SLIDE_OUT_MS, easing = FastOutSlowInEasing)) { -it } +
        fadeOut(tween(FADE_MS))

private fun slideOutToRight(): ExitTransition =
    slideOutHorizontally(tween(SLIDE_OUT_MS, easing = FastOutSlowInEasing)) { it } +
        fadeOut(tween(FADE_MS))

/** Entrada ao navegar p/ frente — não usada (a tela anima via ScreenSlideIn). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzEnterTransition(): EnterTransition {
    return EnterTransition.None
}

/** Saída ao navegar p/ frente — não usada (a tela que entra cobre a atual). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzExitTransition(): ExitTransition {
    return ExitTransition.None
}

/** Entrada ao voltar (pop). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzPopEnterTransition(): EnterTransition {
    val fromTab = initialState.destination.tabIndex() // tela que está saindo
    val toTab = targetState.destination.tabIndex() // tela p/ onde volta
    return when {
        // Volta entre abas: inverte a direção da ida.
        fromTab != null && toTab != null ->
            if (toTab < fromTab) slideInFromLeft() else slideInFromRight()
        // Detalhe: a tela anterior fica parada e é revelada.
        else -> EnterTransition.None
    }
}

/** Saída ao voltar (pop). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzPopExitTransition(): ExitTransition {
    val fromTab = initialState.destination.tabIndex()
    val toTab = targetState.destination.tabIndex()
    return when {
        // Volta entre abas: a aba atual sai p/ o lado oposto da que entra.
        fromTab != null && toTab != null ->
            if (toTab < fromTab) slideOutToRight() else slideOutToLeft()
        // Detalhe: sai p/ direita revelando a tela anterior.
        else -> slideOutToRight()
    }
}
EOF

# --- 3. app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt" <<'EOF'
package com.vztechcore.vztune.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.compose.rememberNavController
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.models.isLocalPlaylistId
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.feedback.EclipseBackground
import com.vztechcore.vztune.ui.components.feedback.rememberDominantColor
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.sheets.TrackMenuContext
import com.vztechcore.vztune.ui.components.sheets.TrackMenuViewModel
import com.vztechcore.vztune.ui.components.sheets.TrackOptionsSheet
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

private data class ActiveTrackMenu(
    val track: Track,
    val context: TrackMenuContext = TrackMenuContext.DEFAULT,
    val localPlaylistId: String? = null,
)

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

    // Última aba visitada — dá o sentido do slide nas trocas de aba.
    // Só atualiza quando a tela atual É uma aba (detalhe não conta).
    var lastTab by remember { mutableStateOf(AppTab.HOME) }
    fun tabFromLeft(tab: AppTab): Boolean = tab.ordinal < lastTab.ordinal
    LaunchedEffect(backStackEntry) {
        val tab = when {
            backStackEntry?.destination?.hasRoute<Routes.Search>() == true -> AppTab.SEARCH
            backStackEntry?.destination?.hasRoute<Routes.Analytics>() == true -> AppTab.ANALYTICS
            backStackEntry?.destination?.hasRoute<Routes.Library>() == true -> AppTab.LIBRARY
            backStackEntry?.destination?.hasRoute<Routes.Home>() == true -> AppTab.HOME
            else -> null
        }
        if (tab != null) lastTab = tab
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
    val bannerVm: DownloadBannerViewModel = hiltViewModel()
    val menuVm: TrackMenuViewModel = hiltViewModel()

    val dominantColor = rememberDominantColor(track?.thumbnailUrl)
    val isMinimized by miniPlayerVm.isMinimized.collectAsStateWithLifecycle()
    val bannerTrack by bannerVm.bannerTrack.collectAsStateWithLifecycle()

    val favoriteKeys by menuVm.favoriteKeys.collectAsStateWithLifecycle()
    val downloadedKeys by menuVm.downloadedKeys.collectAsStateWithLifecycle()
    val activeDownloads by menuVm.activeDownloads.collectAsStateWithLifecycle()
    val localPlaylists by menuVm.localPlaylists.collectAsStateWithLifecycle()
    val resolvedAlbum by menuVm.resolvedAlbum.collectAsStateWithLifecycle()

    var activeMenu by remember { mutableStateOf<ActiveTrackMenu?>(null) }

    fun openTrackMenu(
        targetTrack: Track,
        context: TrackMenuContext = TrackMenuContext.DEFAULT,
        localPlaylistId: String? = null,
    ) {
        menuVm.resolveAlbumFor(targetTrack)
        activeMenu = ActiveTrackMenu(targetTrack, context, localPlaylistId)
    }

    LaunchedEffect(Unit) {
        menuVm.navigationEvent.collect { event ->
            when (event) {
                is TrackMenuViewModel.MenuNavigationEvent.OpenAlbum -> navController.openAlbum(event.album)
                is TrackMenuViewModel.MenuNavigationEvent.OpenArtist -> navController.navigate(
                    Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)
                )
            }
        }
    }

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

    activeMenu?.let { menu ->
        val key = StoredTrack.from(menu.track).key
        TrackOptionsSheet(
            track = menu.track,
            menuContext = menu.context,
            resolvedAlbum = resolvedAlbum,
            isFavorite = key in favoriteKeys,
            isDownloaded = key in downloadedKeys,
            downloadItem = activeDownloads[key],
            localPlaylists = localPlaylists,
            onDismiss = { activeMenu = null },
            onRemoveFromRecentHistory = menuVm::onRemoveFromRecentHistory,
            onRemoveFromLocalPlaylist = { t ->
                menu.localPlaylistId?.let { pid -> menuVm.onRemoveFromLocalPlaylist(pid, t) }
            },
            onToggleFavorite = menuVm::onToggleFavorite,
            onAddToPlaylist = menuVm::onAddToPlaylist,
            onPlayNext = menuVm::onPlayNext,
            onAddToQueue = menuVm::onAddToQueue,
            onDownload = menuVm::onDownload,
            onRemoveDownload = menuVm::onRemoveDownload,
            onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
            onOpenAlbum = { album -> navController.openAlbum(album) },
            onOpenAlbumForTrack = menuVm::onOpenAlbumForTrack,
            onOpenArtistByName = menuVm::onOpenArtistByName,
            onOpenArtistForTrack = menuVm::onOpenArtistForTrack,
            onStartRadio = menuVm::onStartRadio,
            onTrackInfo = { t -> navController.navigateToTrackInfo(t) },
        )
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
                // Entrada: a própria tela anima (ScreenSlideIn) — NavHost não anima.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { vzPopEnterTransition() },
                popExitTransition = { vzPopExitTransition() },
            ) {
                composable<Routes.Home> {
                    ScreenSlideIn(fromLeft = tabFromLeft(AppTab.HOME)) {
                        Placeholder("Home — coming soon")
                    }
                }
                composable<Routes.Analytics> {
                    ScreenSlideIn(fromLeft = tabFromLeft(AppTab.ANALYTICS)) {
                        Placeholder("Analytics — coming soon")
                    }
                }

                composable<Routes.Library> {
                    ScreenSlideIn(fromLeft = tabFromLeft(AppTab.LIBRARY)) {
                        val vm: LibraryViewModel = hiltViewModel()
                        val state by vm.uiState.collectAsStateWithLifecycle()
                        LibraryScreen(
                            state = state,
                            onTabSelected = vm::onTabSelected,
                            onShortcutClick = { navController.navigate(Routes.LibraryList(it.name)) },
                            onTrackClick = vm::onTrackClick,
                            onTrackInfoClick = { t ->
                                openTrackMenu(t, context = TrackMenuContext.RECENT_HISTORY)
                            },
                            onPlaylistClick = { item ->
                                navController.navigate(
                                    Routes.MediaList(
                                        id = item.id,
                                        // Favorito de álbum abre como álbum, senão crashava com key duplicada
                                        isAlbum = item.isAlbum,
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        thumbnailUrl = item.imageUrl,
                                    )
                                )
                            },
                            onCreatePlaylist = vm::createPlaylist,
                        )
                    }
                }

                composable<Routes.LibraryList> {
                    ScreenSlideIn(fromLeft = false) {
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
                }

                composable<Routes.Downloads> {
                    ScreenSlideIn(fromLeft = false) {
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
                }

                composable<Routes.Search> {
                    ScreenSlideIn(fromLeft = tabFromLeft(AppTab.SEARCH)) {
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
                }

                composable<Routes.MediaList> {
                    ScreenSlideIn(fromLeft = false) {
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
                            onOpenArtistByName = vm::onOpenArtistByName,
                            onOpenArtist = vm::onOpenArtist,
                            onAlbumClick = { album ->
                                if (album.id != state.listId) navController.openAlbum(album)
                            },
                            onDeleteLocalPlaylist = vm::onDeleteLocalPlaylist,
                            onRenameLocalPlaylist = vm::onRenameLocalPlaylist,
                            onChangeLocalPlaylistCover = vm::onChangeLocalPlaylistCover,
                        )
                    }
                }

                composable<Routes.Artist> {
                    ScreenSlideIn(fromLeft = false) {
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
                }

                composable<Routes.ArtistList> {
                    ScreenSlideIn(fromLeft = false) {
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
                }

                composable<Routes.TrackInfo> {
                    ScreenSlideIn(fromLeft = false) {
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
}

@Composable
private fun Placeholder(message: String) {
    Box(Modifier.fillMaxSize()) {
        Text(text = message, color = VzOnSurface, modifier = Modifier.align(Alignment.Center))
        AppCredits(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}
EOF

echo "Animacao nas telas aplicada: ScreenSlideIn + NavTransitions + AppNavHost."
