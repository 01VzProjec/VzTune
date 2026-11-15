package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.feedback.rememberDominantColor
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.sheets.MediaListOptionsSheet
import com.vztechcore.vztune.ui.screens.downloads.DownloadItem
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState

/**
 * Playlist / album detail screen.
 *
 * Background rule: the whole screen uses the solid dominant color of the
 * cover. No gradient, no fade to black. When the cover is a Vz color token,
 * the token color itself is the background, so the screen and the cover
 * always match.
 *
 * The album footer now works: [onAlbumClick] is wired from the navigation
 * host, so tapping "More from this artist" opens the tapped album.
 */
@Composable
fun MediaListScreen(
    state: MediaListUiState,
    activeDownloads: Map<String, DownloadItem> = emptyMap(),
    onBackClick: () -> Unit = {},
    onTabSelected: (MediaListTab) -> Unit = {},
    onTrackClick: (Track) -> Unit = {},
    onPlayAllClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onSortSelected: (MediaListSortOrder) -> Unit = {},
    onReorder: (Int, Int) -> Unit = { _, _ -> },
    onStartShuffle: () -> Unit = {},
    onShuffleOnce: () -> Unit = {},
    onStopShuffle: () -> Unit = {},
    onPlayNextClick: (Track) -> Unit = {},
    onTrackInfoClick: (Track) -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onPlayNextAll: () -> Unit = {},
    onAddToQueueAll: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
    onOpenDownloadsScreen: () -> Unit = {},
    onOpenArtistByName: (String) -> Unit = {},
    onOpenArtist: () -> Unit = {},
    onDeleteLocalPlaylist: () -> Unit = {},
    onRenameLocalPlaylist: (String) -> Unit = {},
    onChangeLocalPlaylistCover: (android.net.Uri) -> Unit = {},
    onChangeCoverColor: (String) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Source url for the dominant color. Vz color tokens are resolved
    // directly to their palette color, they are not loaded as images.
    val chameleonSourceUrl = remember(state.thumbnailUrl, state.mosaicThumbnails) {
        if (PlaylistCoverPalette.isDefaultColorToken(state.thumbnailUrl)) null else state.thumbnailUrl
    }
    val extracted = rememberDominantColor(chameleonSourceUrl)
    val dominantColor = remember(state.thumbnailUrl, extracted) {
        if (PlaylistCoverPalette.isDefaultColorToken(state.thumbnailUrl)) {
            PlaylistCoverPalette.resolveBackgroundColor(state.thumbnailUrl, state.listId)
        } else {
            extracted
        }
    }

    val listState = rememberLazyListState()
    val isCollapsed by rememberHeaderCollapsed(listState)
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showListOptionsSheet by remember { mutableStateOf(false) }

    val displayedTracks = state.displayedTracks
    val canReorder = state.isLocal && state.selectedTab == MediaListTab.TRACKS
    val reorderableState = rememberReorderableLazyListState(
        listState = listState,
        onMove = { from, to -> onReorder(from.index, to.index) },
    )

    // Artist click from header / footer. Uses the ViewModel navigation event.
    val handleHeaderArtistClick: () -> Unit = {
        if (state.isAlbum) {
            onOpenArtist()
        } else if (!state.owner.isNullOrBlank()) {
            onOpenArtistByName(state.owner)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(dominantColor)
    ) {
        LazyColumn(
            state = if (canReorder) reorderableState.listState else listState,
            modifier = Modifier
                .fillMaxSize()
                .background(dominantColor),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "header") {
                MediaListExpandedHeader(
                    state = state,
                    dominantColor = dominantColor,
                    onBackClick = onBackClick,
                    onSearchClick = { showSearchOverlay = true },
                    onMoreClick = { showListOptionsSheet = true },
                    onPlayAllClick = onPlayAllClick,
                    onShuffleClick = onShuffleClick,
                    onFavoriteClick = onFavoriteClick,
                    onHeaderArtistClick = handleHeaderArtistClick,
                    onPlayNextAll = onPlayNextAll,
                    onAddToQueueAll = onAddToQueueAll,
                    onDownloadAll = onDownloadAll,
                )
            }

            item(key = "tabs") {
                MediaListTabs(
                    selected = state.selectedTab,
                    onSelect = onTabSelected,
                )
            }

            when {
                state.isLoading -> {
                    item(key = "loading") {
                        MediaListLoadingPlaceholder()
                    }
                }
                displayedTracks.isEmpty() -> {
                    item(key = "empty") {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Nenhuma faixa por aqui.", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
                else -> {
                    itemsIndexed(
                        items = displayedTracks,
                        key = { _, t -> "${t.source.name}_${t.id}" },
                    ) { index, track ->
                        val isCurrent = LocalNowPlayingSafe.isCurrent(track)
                        val isPlaying = isCurrent && LocalNowPlayingSafe.isPlaying()
                        val isPaused = isCurrent && !LocalNowPlayingSafe.isPlaying()
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
                            val itemKey = "${track.source.name}_${track.id}"
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
                                    dragHandleModifier = Modifier.detectReorderAfterLongPress(reorderableState),
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
                thumbnailUrl = state.thumbnailUrl,
                dominantColor = dominantColor,
                onBackClick = onBackClick,
                onSearchClick = { showSearchOverlay = true },
                onMoreClick = { showListOptionsSheet = true },
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

        if (showListOptionsSheet) {
            MediaListOptionsSheet(
                state = state,
                allTracksDownloaded = state.allTracksDownloaded,
                isDownloadingBatch = state.isDownloadingBatch,
                batchDownloadProgress = state.batchDownloadProgress,
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
                onChangeCoverColor = onChangeCoverColor,
            )
        }
    }
}
