package com.vztechcore.vztune.ui.screens.medialist

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.ui.components.download.LocalDownloadedKeys
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.sheets.MediaListArtistPickerSheet
import com.vztechcore.vztune.ui.components.sheets.MediaListOptionsSheet
import com.vztechcore.vztune.ui.components.sheets.parseMediaListArtists
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val HEADER_OFFSET = 1

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaListScreen(
    state: MediaListUiState,
    activeDownloads: Map<String, TrackDownloadItem> = emptyMap(),
    onBackClick: () -> Unit,
    onTabSelected: (MediaListTab) -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSortSelected: (SortOrder) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onStartShuffle: () -> Unit,
    onShuffleOnce: () -> Unit,
    onStopShuffle: () -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onFavoriteClick: () -> Unit,
    onPlayNextAll: () -> Unit = {},
    onAddToQueueAll: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
    onOpenDownloadsScreen: () -> Unit = {},
    onOpenArtist: () -> Unit = {},
    onOpenArtistByName: (String) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onDeleteLocalPlaylist: () -> Unit = {},
    onRenameLocalPlaylist: (String) -> Unit = {},
    onChangeLocalPlaylistCover: (Uri) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val nowPlaying = LocalNowPlaying.current
    val downloadedKeys = LocalDownloadedKeys.current
    var showSortSheet by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showListOptionsSheet by remember { mutableStateOf(false) }
    var showHeaderArtistPicker by remember { mutableStateOf(false) }
    var shuffleJob by remember { mutableStateOf<Job?>(null) }

    val isCollapsed by rememberHeaderCollapsed(listState)

    // Fundo preto AMOLED - sem camaleao, sem gradiente de cor dominante
    val dominantColor = Color.Black
    // Thumb do header colapsado: se a capa for token de cor, usa o mosaico
    val collapsedThumbnailUrl = remember(state.thumbnailUrl, state.mosaicThumbnails) {
        val raw = state.thumbnailUrl
        if (!raw.isNullOrBlank() && !PlaylistCoverPalette.isDefaultColorToken(raw)) raw
        else state.mosaicThumbnails.firstOrNull()
    }

    val ownerArtists = remember(state.owner, state.isLocal) {
        if (!state.isLocal) parseMediaListArtists(state.owner.orEmpty()) else emptyList()
    }

    // FIX 2: playlist do YouTube sem UC... (canal real) -> sem clique no artista.
    // Antes clicava e caía na busca por nome no YT Music, abrindo artista errado.
    // Com UC..., o clique abre o canal. Álbum / playlist YT Music mantém o comportamento.
    val isYouTubePlaylist = !state.isAlbum && !state.isLocal &&
        state.allTracks.isNotEmpty() && state.allTracks.all { it.source == Source.YOUTUBE }
    val hasRealChannel = state.artistId?.startsWith("UC") == true
    val handleHeaderArtistClick: (() -> Unit)? = remember(state.isLocal, state.owner, ownerArtists, isYouTubePlaylist, hasRealChannel) {
        if (state.isLocal || state.owner.isNullOrBlank()) {
            null
        } else if (isYouTubePlaylist && !hasRealChannel) {
            null
        } else if (ownerArtists.size > 1 && !isYouTubePlaylist) {
            { showHeaderArtistPicker = true }
        } else {
            onOpenArtist
        }
    }

    val displayedTracks = remember(state.filteredTracks, state.sortOrder, state.manualOrder, state.selectedTab, state.isAlbum) {
        state.getDisplayTracks()
    }

    val allTracksDownloaded = remember(state.allTracks, downloadedKeys) {
        state.allTracks.isNotEmpty() && state.allTracks.all {
            StoredTrack.from(it).key in downloadedKeys
        }
    }

    val batchDownloadState = remember(state.allTracks, downloadedKeys, activeDownloads) {
        if (state.allTracks.isEmpty()) {
            false to 0f
        } else {
            var activeCount = 0
            var totalProgress = 0f
            state.allTracks.forEach { t ->
                val k = StoredTrack.from(t).key
                val item = activeDownloads[k]
                when {
                    k in downloadedKeys -> totalProgress += 1f
                    item != null && (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) -> {
                        activeCount++
                        totalProgress += item.progress.coerceIn(0f, 1f)
                    }
                    item != null && item.status == DownloadStatus.PAUSED -> {
                        totalProgress += item.progress.coerceIn(0f, 1f)
                    }
                }
            }
            val isRunning = activeCount > 0
            val avgProgress = (totalProgress / state.allTracks.size.toFloat()).coerceIn(0f, 1f)
            isRunning to avgProgress
        }
    }

    val canReorder = !state.isAlbum && state.sortOrder == SortOrder.MANUAL && state.selectedTab == MediaListTab.ALL

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (!canReorder) return@rememberReorderableLazyListState
        val fromTrack = from.index - HEADER_OFFSET
        val toTrack = to.index - HEADER_OFFSET
        if (fromTrack !in displayedTracks.indices) return@rememberReorderableLazyListState
        if (toTrack !in displayedTracks.indices) return@rememberReorderableLazyListState
        onReorder(fromTrack, toTrack)
    }

    LaunchedEffect(state.isShuffling) {
        if (state.isShuffling) {
            shuffleJob?.cancel()
            shuffleJob = launch {
                while (isActive) {
                    onShuffleOnce()
                    delay(150)
                }
            }
            delay(1500)
            onStopShuffle()
        } else {
            shuffleJob?.cancel()
        }
    }

    if (showHeaderArtistPicker && ownerArtists.size > 1) {
        MediaListArtistPickerSheet(
            artists = ownerArtists,
            onPick = { selectedArtist ->
                showHeaderArtistPicker = false
                onOpenArtistByName(selectedArtist)
            },
            onDismiss = { showHeaderArtistPicker = false },
        )
    }

    if (showSortSheet) {
        MediaListSortSheet(
            currentSort = state.sortOrder,
            onSelect = { onSortSelected(it); showSortSheet = false },
            onDismiss = { showSortSheet = false },
        )
    }

    if (showListOptionsSheet) {
        MediaListOptionsSheet(
            state = state,
            allTracksDownloaded = allTracksDownloaded,
            isDownloadingBatch = batchDownloadState.first,
            batchDownloadProgress = batchDownloadState.second,
            onDismiss = { showListOptionsSheet = false },
            onToggleFavorite = onFavoriteClick,
            onPlayNextAll = onPlayNextAll,
            onAddToQueueAll = onAddToQueueAll,
            onDownloadAll = onDownloadAll,
            onOpenDownloadsScreen = onOpenDownloadsScreen,
            onOpenArtistByName = onOpenArtistByName,
            onDeleteLocalPlaylist = onDeleteLocalPlaylist,
            onRenameLocalPlaylist = onRenameLocalPlaylist,
            onChangeLocalPlaylistCover = onChangeLocalPlaylistCover,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            item(key = "header") {
                MediaListExpandedHeader(
                    state = state,
                    dominantColor = dominantColor,
                    allTracksDownloaded = allTracksDownloaded,
                    onBackClick = onBackClick,
                    onTabSelected = onTabSelected,
                    onPlayAllClick = onPlayAllClick,
                    onShuffleClick = onShuffleClick,
                    onSearchClick = { showSearchOverlay = true },
                    onMoreClick = { showListOptionsSheet = true },
                    onSortClick = { showSortSheet = true },
                    onShuffleToggle = {
                        if (state.isShuffling) onStopShuffle() else onStartShuffle()
                    },
                    onFavoriteClick = onFavoriteClick,
                    onArtistClick = handleHeaderArtistClick,
                )
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VzLoading(Modifier.size(160.dp))
                    }
                }
                state.errorMessage != null -> item(key = "error") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(state.errorMessage, color = Color.Red)
                    }
                }
                displayedTracks.isEmpty() -> item(key = "empty") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Nenhuma faixa por aqui.", color = Color.White.copy(alpha = 0.6f))
                    }
                }
                else -> itemsIndexed(
                    items = displayedTracks,
                    // Key com índice: mesmo se o InnerTube devolver a mesma faixa 2x, nunca duplica e nunca crasha
                    key = { index, t -> "${t.source.name}_${t.id}_$index" },
                ) { index, track ->
                    val isCurrent = nowPlaying.isCurrent(track.id, track.source)
                    val isPlaying = isCurrent && nowPlaying.isPlaying
                    val isPaused = isCurrent && !nowPlaying.isPlaying

                    val useVideoLayout = state.selectedTab == MediaListTab.VIDEOS

                    if (useVideoLayout) {
                        MediaListVideoCard(
                            track = track,
                            isPlaying = isPlaying,
                            isPaused = isPaused,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onTrackInfoClick(track) },
                        )
                    } else if (canReorder) {
                        val itemKey = "${track.source.name}_${track.id}_$index"
                        ReorderableItem(reorderableState, key = itemKey) { isDragging ->
                            MediaListTrackRow(
                                track = track,
                                index = if (state.isAlbum) index + 1 else null,
                                isPlaying = isPlaying,
                                isPaused = isPaused,
                                onClick = { onTrackClick(track) },
                                onMoreClick = { onTrackInfoClick(track) },
                                onPlayNext = { onPlayNextClick(track) },
                                showDragHandle = true,
                                dragHandleModifier = Modifier.longPressDraggableHandle(),
                                modifier = Modifier
                                    .animateItem(
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        )
                                    )
                                    .background(Color.Black.copy(alpha = if (isDragging) 0.5f else 0f)),
                            )
                        }
                    } else {
                        MediaListTrackRow(
                            track = track,
                            index = if (state.isAlbum) index + 1 else null,
                            isPlaying = isPlaying,
                            isPaused = isPaused,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onTrackInfoClick(track) },
                            onPlayNext = { onPlayNextClick(track) },
                            showDragHandle = false,
                            dragHandleModifier = Modifier,
                            modifier = Modifier,
                        )
                    }
                }
            }

            item(key = "footer") {
                MediaListFooter(
                    state = state,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = handleHeaderArtistClick,
                )
            }
        }

        CollapsedHeaderHost(visible = isCollapsed) {
            MediaListCollapsedHeader(
                title = state.title,
                thumbnailUrl = collapsedThumbnailUrl,
                dominantColor = dominantColor,
                onBackClick = onBackClick,
                onSearchClick = { showSearchOverlay = true },
                onMoreClick = { showListOptionsSheet = true },
                showFavorite = state.showFavorite,
                isFavorite = state.isFavorite,
                onFavoriteClick = onFavoriteClick,
            )
        }

        if (showSearchOverlay) {
            MediaListSearchOverlay(
                allTracks = displayedTracks,
                isAlbum = state.isAlbum,
                onClose = { showSearchOverlay = false },
                onTrackClick = onTrackClick,
                onPlayNextClick = onPlayNextClick,
                onTrackInfoClick = onTrackInfoClick,
            )
        }
    }
}
