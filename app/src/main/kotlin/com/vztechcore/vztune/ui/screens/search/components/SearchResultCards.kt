package com.vztechcore.vztune.ui.screens.search.components

// Unificacao: SwipeableRow, RowCard e VideoResultCard duplicados foram para components/rows e components/cards.
// Aqui so ficou a lista + o mapeamento SearchHit -> card. Sem logica duplicada.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.cards.VzRowCard
import com.vztechcore.vztune.ui.components.cards.VzVideoCard
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.components.rows.SwipeableRow
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.MoreVert

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsContent(
    hits: List<SearchHit>,
    isLoadingMore: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onHitClick: (SearchHit) -> Unit,
    onPlayNextClick: (Track) -> Unit,
    onTrackMoreClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVideoMode = hits.all { it is SearchHit.VideoHit } && hits.isNotEmpty()
    val isShortsMode = hits.all { it is SearchHit.ShortHit } && hits.isNotEmpty()
    if (isShortsMode) {
        ShortsGridContent(
            hits = hits.filterIsInstance<SearchHit.ShortHit>(),
            isLoadingMore = isLoadingMore,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
            onHitClick = onHitClick,
            onTrackMoreClick = onTrackMoreClick,
            modifier = modifier,
        )
        return
    }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            val last = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = lazyListState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }.collect { nearEnd -> if (nearEnd) onLoadMore() }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn(
            state = lazyListState,
            // Tamanho igual a playlist: linha ja da 16dp esq / 8dp dir, entao o pai nao repete padding lateral.
            // Video mantem 12dp do pai + VzVideoCard 0dp = 12dp, igual MediaList.
            contentPadding = PaddingValues(bottom = 160.dp, top = 8.dp, start = if (isVideoMode) 12.dp else 0.dp, end = if (isVideoMode) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(if (isVideoMode) 20.dp else 0.dp),
        ) {
            itemsIndexed(hits, key = { _, item -> item.stableKey }) { _, hit ->
                if (isVideoMode && hit is SearchHit.VideoHit) {
                    VideoResultCard(
                        hit = hit,
                        onClick = { onHitClick(hit) },
                        onMoreClick = { onTrackMoreClick(hit.track) },
                    )
                } else {
                    SearchResultCard(
                        hit = hit,
                        onClick = { onHitClick(hit) },
                        onMoreClick = {
                            val track = when (hit) {
                                is SearchHit.SongHit -> hit.track
                                is SearchHit.VideoHit -> hit.track
                                is SearchHit.ShortHit -> hit.track
                                else -> null
                            }
                            if (track != null) onTrackMoreClick(track)
                        },
                        onPlayNextClick = onPlayNextClick,
                    )
                }
            }

            if (isLoadingMore) {
                item(key = "loading_more") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VzLoading(Modifier.size(96.dp))
                    }
                }
            }
            if (!isLoadingMore) {
                item(key = "credits") { AppCredits() }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    hit: SearchHit,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayNextClick: (Track) -> Unit,
) {
    val nowPlaying = LocalNowPlaying.current

    when (hit) {
        is SearchHit.SongHit -> {
            val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
            SwipeableRow(onPlayNext = { onPlayNextClick(hit.track) }) {
                VzRowCard(
                    imageUrl = hit.track.thumbnailUrl, shape = ThumbnailShape.SQUARE, title = hit.track.title,
                    subtitle = { TrackSubtitle(hit.track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f)) },
                    durationLabel = formatDuration(hit.track.durationSeconds),
                    isPlaying = isCurrent && nowPlaying.isPlaying, isPaused = isCurrent && !nowPlaying.isPlaying,
                    onClick = onClick, onMoreClick = onMoreClick, showMoreButton = true,
                )
            }
        }
        is SearchHit.VideoHit -> {
            val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
            SwipeableRow(onPlayNext = { onPlayNextClick(hit.track) }) {
                VzRowCard(
                    imageUrl = hit.track.thumbnailUrl, shape = ThumbnailShape.SQUARE, title = hit.track.title,
                    subtitle = { TrackSubtitle(hit.track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f)) },
                    durationLabel = null,
                    isPlaying = isCurrent && nowPlaying.isPlaying, isPaused = isCurrent && !nowPlaying.isPlaying,
                    onClick = onClick, onMoreClick = onMoreClick, showMoreButton = true,
                )
            }
        }
        is SearchHit.ShortHit -> {
            val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
            SwipeableRow(onPlayNext = { onPlayNextClick(hit.track) }) {
                VzRowCard(
                    imageUrl = hit.track.thumbnailUrl, shape = ThumbnailShape.SQUARE, title = hit.track.title,
                    subtitle = { TrackSubtitle(hit.track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f)) },
                    durationLabel = formatDuration(hit.track.durationSeconds),
                    isPlaying = isCurrent && nowPlaying.isPlaying, isPaused = isCurrent && !nowPlaying.isPlaying,
                    onClick = onClick, onMoreClick = onMoreClick, showMoreButton = true,
                )
            }
        }
        is SearchHit.PlaylistHit -> {
            val owner = hit.playlist.owner?.takeIf { it.isNotBlank() }
            val views = hit.playlist.views?.takeIf { it.isNotBlank() }
            val subtitleText = listOfNotNull(owner, views)
                .joinToString(" • ")
                .ifBlank { "Playlist" }
            VzRowCard(
                imageUrl = hit.playlist.thumbnailUrl,
                mosaic = hit.playlist.mosaicThumbnails,
                shape = ThumbnailShape.SQUARE,
                title = hit.playlist.title,
                subtitle = {
                    Text(
                        subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                durationLabel = null,
                isPlaying = false,
                isPaused = false,
                onClick = onClick,
                onMoreClick = onMoreClick,
                showMoreButton = false,
            )
        }
        is SearchHit.ArtistHit -> VzRowCard(
            imageUrl = hit.artist.thumbnailUrl, shape = ThumbnailShape.CIRCLE, title = hit.artist.name,
            subtitle = { Text("Artista", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f)) },
            durationLabel = null, isPlaying = false, isPaused = false, onClick = onClick, onMoreClick = onMoreClick, showMoreButton = false,
        )
        is SearchHit.AlbumHit -> {
            val artist = hit.album.artist.takeIf { it.isNotBlank() }
            val views = hit.album.views?.takeIf { it.isNotBlank() }
            val year = hit.album.year?.toString()?.takeIf { it.isNotBlank() }
            val subtitleText = listOfNotNull(artist, views ?: year)
                .joinToString(" • ")
                .ifBlank { "Álbum" }
            VzRowCard(
                imageUrl = hit.album.thumbnailUrl,
                shape = ThumbnailShape.SQUARE,
                title = hit.album.title,
                subtitle = {
                    Text(
                        subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                durationLabel = null,
                isPlaying = false,
                isPaused = false,
                onClick = onClick,
                onMoreClick = onMoreClick,
                showMoreButton = false,
            )
        }
    }
}

@Composable
fun VideoResultCard(hit: SearchHit.VideoHit, onClick: () -> Unit, onMoreClick: () -> Unit) {
    // Atalho fino: logica mora em VzVideoCard. Search usa horizontalPadding = 0.dp porque o pai ja da 12.dp.
    val nowPlaying = LocalNowPlaying.current
    val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
    VzVideoCard(track = hit.track, isPlaying = isCurrent && nowPlaying.isPlaying, isPaused = isCurrent && !nowPlaying.isPlaying, onClick = onClick, onMoreClick = onMoreClick, horizontalPadding = 0.dp)
}

private fun formatDuration(seconds: Long): String? {
    if (seconds <= 0) return null
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsGridContent(
    hits: List<SearchHit.ShortHit>,
    isLoadingMore: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onHitClick: (SearchHit) -> Unit,
    onTrackMoreClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 4
        }.collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(bottom = 160.dp, top = 8.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            gridItemsIndexed(hits, key = { _, item -> item.stableKey }) { _, hit ->
                ShortResultCard(
                    hit = hit,
                    onClick = { onHitClick(hit) },
                    onMoreClick = { onTrackMoreClick(hit.track) },
                )
            }
            if (isLoadingMore) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VzLoading(Modifier.size(96.dp))
                    }
                }
            }
            if (!isLoadingMore) {
                item(span = { GridItemSpan(2) }) { AppCredits() }
            }
        }
    }
}

@Composable
fun ShortResultCard(hit: SearchHit.ShortHit, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val nowPlaying = LocalNowPlaying.current
    val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f))) {
            if (hit.track.thumbnailUrl != null) {
                AsyncImage(
                    model = hit.track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (hit.track.durationSeconds > 0) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        formatDuration(hit.track.durationSeconds) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
            if (isCurrent) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Text(
                        if (nowPlaying.isPlaying) "Tocando" else "Pausado",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(hit.track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                TrackSubtitle(hit.track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f))
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
                Icon(VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
