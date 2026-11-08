package com.vztechcore.vztune.ui.screens.artist.list

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.CoverCard
import com.vztechcore.vztune.ui.components.DetailTopBar
import com.vztechcore.vztune.ui.components.LocalNowPlaying
import com.vztechcore.vztune.ui.components.TrackListRow
import com.vztechcore.vztune.ui.components.VzLoading
import com.vztechcore.vztune.ui.components.playbackOf
import com.vztechcore.vztune.ui.screens.medialist.MediaListVideoCard

/** How many items before the end the next page starts loading. */
private const val LOAD_MORE_THRESHOLD = 5

/**
 * "See all" screen of an artist. Songs and videos are lists, albums / singles /
 * featured playlists are a 2-column grid. All of them keep loading more while scrolling.
 */
@Composable
fun ArtistListScreen(
    state: ArtistListUiState,
    onBackClick: () -> Unit,
    onLoadMore: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        DetailTopBar(title = state.title, subtitle = state.artistName, onBackClick = onBackClick)

        // ---------- Content ----------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VzLoading(Modifier.size(160.dp))
                }

                state.errorMessage != null -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.errorMessage,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }

                state.kind == ArtistListKind.SONGS -> SongsList(state, onLoadMore, onTrackClick, onTrackInfoClick)
                state.kind == ArtistListKind.VIDEOS -> VideosList(state, onLoadMore, onTrackClick, onTrackInfoClick)
                else -> CoversGrid(state, onLoadMore, onAlbumClick, onPlaylistClick)
            }
        }
    }
}

@Composable
private fun SongsList(
    state: ArtistListUiState,
    onLoadMore: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
) {
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, state.itemCount, onLoadMore)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 140.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.tracks, key = { "${it.source.name}_${it.id}" }) { track ->
            TrackListRow(
                track = track,
                onClick = { onTrackClick(track) },
                onMoreClick = { onTrackInfoClick(track) },
            )
        }
        if (state.isLoadingMore) {
            item(key = "loading_more") { LoadingMoreIndicator() }
        }
        item(key = "credits") { AppCredits() }
    }
}

@Composable
private fun VideosList(
    state: ArtistListUiState,
    onLoadMore: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
) {
    val listState = rememberLazyListState()
    val nowPlaying = LocalNowPlaying.current
    LoadMoreEffect(listState, state.itemCount, onLoadMore)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.tracks, key = { "${it.source.name}_${it.id}" }) { track ->
            val playback = nowPlaying.playbackOf(track)
            MediaListVideoCard(
                track = track,
                isPlaying = playback.isPlaying,
                isPaused = playback.isPaused,
                onClick = { onTrackClick(track) },
                onMoreClick = { onTrackInfoClick(track) },
            )
        }
        if (state.isLoadingMore) {
            item(key = "loading_more") { LoadingMoreIndicator() }
        }
        item(key = "credits") { AppCredits() }
    }
}

@Composable
private fun CoversGrid(
    state: ArtistListUiState,
    onLoadMore: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    val gridState = rememberLazyGridState()
    LoadMoreEffect(gridState, state.itemCount, onLoadMore)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.albums, key = { "album_${it.id}" }) { album ->
            CoverCard(
                imageUrl = album.thumbnailUrl,
                title = album.title,
                subtitle = if (state.kind == ArtistListKind.SINGLES) "Single" else "Álbum",
                onClick = { onAlbumClick(album) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(state.playlists, key = { "playlist_${it.id}" }) { playlist ->
            CoverCard(
                imageUrl = playlist.thumbnailUrl,
                title = playlist.title,
                subtitle = playlist.owner,
                onClick = { onPlaylistClick(playlist) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.isLoadingMore) {
            item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) { LoadingMoreIndicator() }
        }
        item(key = "credits", span = { GridItemSpan(maxLineSpan) }) { AppCredits() }
    }
}

@Composable
private fun LoadingMoreIndicator() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        VzLoading(Modifier.size(96.dp))
    }
}

/** Calls [onLoadMore] whenever the user is close to the end; re-checks after each page (itemCount). */
@Composable
private fun LoadMoreEffect(listState: LazyListState, itemCount: Int, onLoadMore: () -> Unit) {
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, itemCount) {
        if (nearEnd) onLoadMore()
    }
}

@Composable
private fun LoadMoreEffect(gridState: LazyGridState, itemCount: Int, onLoadMore: () -> Unit) {
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, itemCount) {
        if (nearEnd) onLoadMore()
    }
}
