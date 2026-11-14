package com.vztechcore.vztune.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.ui.components.sheets.TrackOptionsSheet
import com.vztechcore.vztune.ui.screens.search.components.PlaylistFilterType
import com.vztechcore.vztune.ui.screens.search.components.SearchAutocomplete
import com.vztechcore.vztune.ui.screens.search.components.SearchBar
import com.vztechcore.vztune.ui.screens.search.components.SearchHome
import com.vztechcore.vztune.ui.screens.search.components.SearchErrorState
import com.vztechcore.vztune.ui.screens.search.components.SearchLoadingState
import com.vztechcore.vztune.ui.screens.search.components.SearchNoResultsState
import com.vztechcore.vztune.ui.screens.search.components.SearchPlaylistFilterTab
import com.vztechcore.vztune.ui.screens.search.components.SearchResultsContent

@Composable
fun SearchScreen(
    state: SearchUiState,
    favoriteKeys: Set<String> = emptySet(),
    localPlaylists: List<LocalPlaylist> = emptyList(),
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onFilterSelect: (SearchFilter) -> Unit,
    onSubmit: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onLoadMore: () -> Unit,
    onHitClick: (SearchHit) -> Unit = {},
    onPlayNextClick: (Track) -> Unit = {},
    onAddToQueueClick: (Track) -> Unit = {},
    onToggleFavorite: (Track) -> Unit = {},
    onAddToPlaylist: (playlistId: String, track: Track) -> Unit = { _, _ -> },
    onOpenArtistByName: (String) -> Unit = {},
    onTrackInfoClick: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var playlistFilter by remember { mutableStateOf(PlaylistFilterType.ALL) }
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }

    selectedTrackForMenu?.let { track ->
        val isFav = StoredTrack.from(track).key in favoriteKeys
        TrackOptionsSheet(
            track = track,
            isFavorite = isFav,
            localPlaylists = localPlaylists,
            onDismiss = { selectedTrackForMenu = null },
            onToggleFavorite = { onToggleFavorite(track) },
            onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, track) },
            onPlayNext = { onPlayNextClick(track) },
            onAddToQueue = { onAddToQueueClick(track) },
            onOpenArtist = { artistName -> onOpenArtistByName(artistName) },
            onTrackInfoClick = { onTrackInfoClick(track) },
        )
    }

    val displayedHits = remember(state.hits, state.selectedFilter, playlistFilter) {
        if (state.selectedFilter == SearchFilter.PLAYLISTS) {
            val playlistHits = state.hits.filterIsInstance<SearchHit.PlaylistHit>()
            val otherHits = state.hits.filter { it !is SearchHit.PlaylistHit }

            val filteredPlaylists = when (playlistFilter) {
                PlaylistFilterType.MUSIC -> playlistHits.filter { it.playlist.source == Source.YOUTUBE_MUSIC }
                PlaylistFilterType.VIDEO -> playlistHits.filter { it.playlist.source == Source.YOUTUBE }
                PlaylistFilterType.ALL -> {
                    val ytm = playlistHits.filter { it.playlist.source == Source.YOUTUBE_MUSIC }
                    val yt = playlistHits.filter { it.playlist.source == Source.YOUTUBE }
                    val interleaved = mutableListOf<SearchHit.PlaylistHit>()
                    val maxLen = maxOf(ytm.size, yt.size)
                    for (i in 0 until maxLen) {
                        if (i < ytm.size) interleaved.add(ytm[i])
                        if (i < yt.size) interleaved.add(yt[i])
                    }
                    interleaved
                }
            }
            otherHits + filteredPlaylists
        } else {
            state.hits
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        SearchBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onFocusChanged = onFocusChanged,
            onSubmit = onSubmit,
            selectedFilter = state.selectedFilter,
            onFilterSelect = onFilterSelect,
            showFilters = state.hasSearched,
        )

        if (state.hasSearched && !state.showHomeState && !state.showSuggestionsState && state.selectedFilter == SearchFilter.PLAYLISTS) {
            SearchPlaylistFilterTab(
                selected = playlistFilter,
                onSelect = { playlistFilter = it }
            )
        }

        when {
            state.showHomeState -> SearchHome(
                recentTracks = state.recentTracks,
                history = state.history,
                onPickTrack = { track ->
                    val hit = if (track.source == Source.YOUTUBE) SearchHit.VideoHit(track) else SearchHit.SongHit(track)
                    onHitClick(hit)
                },
                onPickQuery = { q ->
                    onQueryChange(q)
                    onSubmit()
                },
                onRemoveHistory = onRemoveHistory,
                onClearHistory = onClearHistory,
            )
            state.showSuggestionsState -> SearchAutocomplete(
                query = state.query,
                suggestions = state.suggestions,
                onPickQuery = { q ->
                    onQueryChange(q)
                    onSubmit()
                },
                onPickSong = { track -> onHitClick(SearchHit.SongHit(track)) },
            )
            state.isLoading -> SearchLoadingState(video = state.selectedFilter == SearchFilter.VIDEOS)
            state.errorMessage != null -> SearchErrorState(
                message = state.errorMessage,
                onRetry = onSubmit,
            )
            state.isEmptyResult || (state.selectedFilter == SearchFilter.PLAYLISTS && displayedHits.isEmpty()) -> SearchNoResultsState(query = state.query)
            else -> SearchResultsContent(
                hits = displayedHits,
                isLoadingMore = state.isLoadingMore,
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onHitClick = onHitClick,
                onPlayNextClick = onPlayNextClick,
                onTrackMoreClick = { track -> selectedTrackForMenu = track },
            )
        }
    }
}

@Composable
fun SearchYouTubePlaylistCard(
    hit: SearchHit.PlaylistHit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            AsyncImage(
                model = hit.playlist.thumbnailUrl,
                contentDescription = hit.playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = hit.playlist.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${hit.playlist.owner} • Playlist",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}