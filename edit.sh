#!/bin/bash
set -e

# VzTune - Navegacao IMEDIATA (sem o 'delay pra comecar')
# Rode na raiz do projeto VzTune.
#
# O que causava o delay percebido:
# 1) exitTransition = None fazia a tela atual SUMIR no 1o frame e a nova
#    deslizar por cima de um fundo preto. Esse flash preto parecia delay.
#    Agora a tela atual fica PARADA enquanto a nova desliza por cima dela.
# 2) EclipseBackground animava o fundo por 900 ms (3x o slide) e ficava
#    'arrastando' depois da transicao. Agora 300 ms, igual ao slide.
# 3) A busca abria o teclado no meio do slide -> resize + recomposicao
#    travavam o slide. Agora o teclado espera o slide terminar (320 ms).
#
# Sobre Debug vs Release: Debug E mais lento mesmo (sem R8, sem baseline
# profile, checks do Compose). Em Release vai ficar mais rapido ainda,
# mas esses 3 fixes acima resolvem o delay que era de codigo e aparecia
# ate em Release.

if [ ! -f "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt" ]; then
  echo "ERRO: rode este script na raiz do projeto VzTune."
  exit 1
fi

# --- 1. app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/NavTransitions.kt ---
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

/**
 * Saída ao navegar p/ frente — SEGURA a tela atual parada enquanto a nova
 * desliza por cima dela (efeito "passando por cima").
 *
 * Antes era ExitTransition.None: a tela atual sumia no 1º frame e a nova
 * deslizava por cima de um fundo preto — esse flash preto é o que parecia
 * "delay pra começar". Com o hold, a transição é contínua e parece imediata:
 * clicou, a nova já desliza por cima da atual.
 *
 * Dura o mesmo que o slide da ScreenSlideIn (300 ms). Depois disso o NavHost
 * remove a tela antiga.
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.vzHoldTransition(): ExitTransition {
    // slideOut de 0 = não sai do lugar; fadeOut lento só p/ suavizar a remoção.
    return slideOutHorizontally(tween(SLIDE_OUT_MS + 20, easing = FastOutSlowInEasing)) { 0 } +
        fadeOut(tween(SLIDE_OUT_MS + 20))
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

# --- 2. app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt ---
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
                // Saída: SEGURA a tela atual parada enquanto a nova desliza por cima.
                // Antes era None e a tela atual sumia na hora -> flash preto que
                // parecia "delay pra começar". Agora a transição é contínua.
                exitTransition = { vzHoldTransition() },
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

# --- 3. app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback/EclipseBackground.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback/EclipseBackground.kt" <<'EOF'
package com.vztechcore.vztune.ui.components.feedback

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts the most vibrant color from an image URL.
 * Returns Color.Black if the image is missing or extraction fails.
 */
@Composable
fun rememberDominantColor(url: String?): Color {
    var color by remember { mutableStateOf(Color.Black) }
    val context = LocalContext.current

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            color = Color.Black
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(96)
                    .build()
                val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
                val bitmap = drawable?.toBitmap()
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    Color(
                        palette.getVibrantColor(
                            palette.getDominantColor(android.graphics.Color.BLACK)
                        )
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
        if (extracted != null) color = extracted
    }
    return color
}

/**
 * App-wide background gradient: the dominant color of the current image
 * (top) fading into black (bottom).
 */
@Composable
fun EclipseBackground(
    baseColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // 300 ms = igual ao slide das telas. Antes era 900 ms e o fundo ficava
    // "arrastando" 600 ms depois do slide terminar, o que fazia o app
    // parecer lento mesmo com a navegação já tendo terminado.
    val animated by animateColorAsState(
        targetValue = baseColor,
        animationSpec = tween(durationMillis = 300),
        label = "eclipse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to animated.copy(alpha = 0.55f),
                            0.45f to animated.copy(alpha = 0.16f),
                            1.0f to Color.Black,
                        )
                    )
                )
        )
        content()
    }
}

===== Fim do arquivo [app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback/EclipseBackground.kt] =====
EOF

# --- 4. app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt" <<'EOF'
package com.vztechcore.vztune.ui.screens.library.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.FollowedArtist
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.cards.ArtistCircleCard
import com.vztechcore.vztune.ui.components.cards.TrackListRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.navigation.Search
import kotlinx.coroutines.delay

@Composable
internal fun LibraryListSearchOverlay(
    tracks: List<Track>,
    artists: List<FollowedArtist>,
    isArtistMode: Boolean,
    onClose: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onArtistClick: (FollowedArtist) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        // Espera o slide da tela terminar (300 ms) antes de abrir o teclado.
        // Antes o teclado abria no meio do slide -> resize da janela + recomposição
        // travavam o slide e parecia que a tela demorava pra abrir.
        delay(320)
        focusRequester.requestFocus()
    }

    val filteredTracks = remember(tracks, query) {
        if (query.isBlank()) tracks.sortedBy { it.title.lowercase() }
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
        }
    }
    val filteredArtists = remember(artists, query) {
        if (query.isBlank()) artists.sortedBy { it.name.lowercase() }
        else artists.filter { it.name.contains(query, ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0F1E))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            SearchInputBar(
                query = query,
                onQueryChange = { query = it },
                onClose = onClose,
                placeholder = if (isArtistMode) "Pesquisar artista…" else "Pesquisar faixa…",
                focusRequester = focusRequester,
                onSearch = { keyboardController?.hide() },
            )

            val count = if (isArtistMode) filteredArtists.size else filteredTracks.size
            Text(
                text = if (query.isBlank()) "$count ${if (isArtistMode) "artistas" else "faixas"} • A → Z"
                else "$count resultados",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            when {
                isArtistMode && filteredArtists.isEmpty() -> EmptySearchResult(query)
                !isArtistMode && filteredTracks.isEmpty() -> EmptySearchResult(query)

                isArtistMode -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredArtists, key = { it.id }) { artist ->
                        ArtistCircleCard(
                            name = artist.name,
                            imageUrl = artist.thumbnailUrl,
                            onClick = { onArtistClick(artist) },
                            size = 150.dp,
                        )
                    }
                    item(key = "credits", span = { GridItemSpan(maxLineSpan) }) { AppCredits() }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        filteredTracks,
                        key = { _, t -> "${t.source.name}_${t.id}" },
                    ) { _, track ->
                        TrackListRow(
                            track = track,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onTrackInfoClick(track) },
                        )
                    }
                    item(key = "credits") { AppCredits() }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchResult(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = if (query.isBlank()) "Nada por aqui." else "Nada encontrado para \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(VzIcons.Search, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(placeholder, color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VzIcons.Close, "Limpar", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape).clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(VzIcons.Close, "Fechar busca", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

===== Fim do arquivo [app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/list/LibraryListSearchOverlay.kt] =====
EOF

# --- 5. app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt" <<'EOF'
package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.navigation.Search
import kotlinx.coroutines.delay

@Composable
internal fun MediaListSearchOverlay(
    allTracks: List<Track>,
    isAlbum: Boolean = false,
    onClose: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val nowPlaying = LocalNowPlaying.current

    LaunchedEffect(Unit) {
        // Espera o slide da tela terminar (300 ms) antes de abrir o teclado.
        // Antes o teclado abria no meio do slide -> resize + recomposição travavam o slide.
        delay(320)
        focusRequester.requestFocus()
    }

    val filteredTracks = remember(allTracks, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) allTracks
        else allTracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable {
                        keyboardController?.hide()
                        onClose()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VzIcons.Close,
                    contentDescription = "Fechar pesquisa",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = VzIcons.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = if (isAlbum) "Pesquisar no álbum..." else "Pesquisar na playlist...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.45f),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = VzIcons.Close,
                        contentDescription = "Limpar texto",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { query = "" },
                    )
                }
            }
        }

        if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nenhuma faixa encontrada para \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = filteredTracks,
                    key = { index, t -> "search_${t.source.name}_${t.id}_$index" },
                ) { _, track ->
                    val isCurrent = nowPlaying.isCurrent(track.id, track.source)
                    val originalIndex = if (isAlbum) {
                        allTracks.indexOfFirst { it.id == track.id && it.source == track.source }
                            .takeIf { it >= 0 }
                            ?.plus(1)
                    } else {
                        null
                    }

                    MediaListTrackRow(
                        track = track,
                        index = originalIndex,
                        isPlaying = isCurrent && nowPlaying.isPlaying,
                        isPaused = isCurrent && !nowPlaying.isPlaying,
                        onClick = {
                            keyboardController?.hide()
                            onTrackClick(track)
                        },
                        onMoreClick = { onTrackInfoClick(track) },
                        onPlayNext = { onPlayNextClick(track) },
                        showDragHandle = false,
                        dragHandleModifier = Modifier,
                    )
                }
                item(key = "credits_search") { AppCredits() }
            }
        }
    }
}

===== Fim do arquivo [app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListSearchOverlay.kt] =====
EOF

echo "Navegacao imediata aplicada: hold + Eclipse 300ms + teclado apos o slide."
