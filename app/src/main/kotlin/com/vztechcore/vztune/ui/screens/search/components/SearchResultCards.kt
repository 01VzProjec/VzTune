package com.vztechcore.vztune.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.ui.components.LocalNowPlaying
import com.vztechcore.vztune.ui.components.MediaThumbnail
import com.vztechcore.vztune.ui.components.ThumbnailShape
import com.vztechcore.vztune.ui.components.TrackSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsContent(
    hits: List<SearchHit>,
    isLoadingMore: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onHitClick: (SearchHit) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVideoMode = hits.all { it is SearchHit.VideoHit } && hits.isNotEmpty()
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
            contentPadding = PaddingValues(bottom = 160.dp, top = 8.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isVideoMode) 20.dp else 4.dp),
        ) {
            itemsIndexed(hits, key = { _, item -> item.stableKey }) { _, hit ->
                if (isVideoMode && hit is SearchHit.VideoHit) {
                    VideoResultCard(
                        hit = hit,
                        onClick = { onHitClick(hit) },
                        onMoreClick = { /* TODO: menu */ },
                    )
                } else {
                    SearchResultCard(
                        hit = hit,
                        onClick = { onHitClick(hit) },
                        onMoreClick = { /* TODO: menu */ },
                        onTrackInfoClick = onTrackInfoClick,
                    )
                }
            }

            if (isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    hit: SearchHit,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onTrackInfoClick: (Track) -> Unit,
) {
    val nowPlaying = LocalNowPlaying.current

    when (hit) {
        is SearchHit.SongHit -> {
            val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
            SwipeableRow(
                onInfoRequested = { onTrackInfoClick(hit.track) },
            ) {
                RowCard(
                    imageUrl = hit.track.thumbnailUrl,
                    shape = ThumbnailShape.SQUARE,
                    title = hit.track.title,
                    subtitle = {
                        TrackSubtitle(
                            track = hit.track,
                            style = MaterialTheme.typography.bodySmall,
                            prefixColor = Color.White.copy(alpha = 0.55f),
                            artistColor = Color.White.copy(alpha = 0.6f),
                        )
                    },
                    durationLabel = formatDuration(hit.track.durationSeconds),
                    isPlaying = isCurrent && nowPlaying.isPlaying,
                    isPaused = isCurrent && !nowPlaying.isPlaying,
                    onClick = onClick,
                    onMoreClick = onMoreClick,
                    showMoreButton = true,
                )
            }
        }
        is SearchHit.VideoHit -> {
            val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
            SwipeableRow(
                onInfoRequested = { onTrackInfoClick(hit.track) },
            ) {
                RowCard(
                    imageUrl = hit.track.thumbnailUrl,
                    shape = ThumbnailShape.SQUARE,
                    title = hit.track.title,
                    subtitle = {
                        TrackSubtitle(
                            track = hit.track,
                            style = MaterialTheme.typography.bodySmall,
                            prefixColor = Color.White.copy(alpha = 0.55f),
                            artistColor = Color.White.copy(alpha = 0.6f),
                        )
                    },
                    // Sem duração para vídeos
                    durationLabel = null,
                    isPlaying = isCurrent && nowPlaying.isPlaying,
                    isPaused = isCurrent && !nowPlaying.isPlaying,
                    onClick = onClick,
                    onMoreClick = onMoreClick,
                    showMoreButton = true,
                )
            }
        }
        is SearchHit.PlaylistHit -> RowCard(
            imageUrl = hit.playlist.thumbnailUrl,
            mosaic = hit.playlist.mosaicThumbnails,
            shape = ThumbnailShape.SQUARE,
            title = hit.playlist.title,
            subtitle = {
                Text(
                    "Playlist · ${hit.playlist.owner ?: ""}",
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
            showMoreButton = false, // ❌ Sem 3 pontinhos
        )
        is SearchHit.ArtistHit -> RowCard(
            imageUrl = hit.artist.thumbnailUrl,
            shape = ThumbnailShape.CIRCLE,
            title = hit.artist.name,
            subtitle = {
                Text("Artista", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            },
            durationLabel = null,
            isPlaying = false,
            isPaused = false,
            onClick = onClick,
            onMoreClick = onMoreClick,
            showMoreButton = false, // ❌ Sem 3 pontinhos
        )
        is SearchHit.AlbumHit -> RowCard(
            imageUrl = hit.album.thumbnailUrl,
            shape = ThumbnailShape.SQUARE,
            title = hit.album.title,
            subtitle = {
                Text(
                    "Álbum · ${hit.album.artist}",
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
            showMoreButton = false, // ❌ Sem 3 pontinhos
        )
    }
}

@Composable
fun VideoResultCard(
    hit: SearchHit.VideoHit,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val nowPlaying = LocalNowPlaying.current
    val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            MediaThumbnail(
                imageUrl = hit.track.thumbnailUrl,
                shape = ThumbnailShape.WIDE,
                cornerRadius = 12.dp,
                isPlaying = isCurrent && nowPlaying.isPlaying,
                isPaused = isCurrent && !nowPlaying.isPlaying,
            )
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mais", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            hit.track.title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        TrackSubtitle(
            track = hit.track,
            style = MaterialTheme.typography.bodySmall,
            prefixColor = Color.White.copy(alpha = 0.55f),
            artistColor = Color.White.copy(alpha = 0.6f),
        )
    }
}

/**
 * Wraps any Row with a "swipe-right to reveal info" gesture.
 * If released past 40% of the screen width, calls onInfoRequested.
 */
@Composable
private fun SwipeableRow(
    onInfoRequested: () -> Unit,
    content: @Composable () -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var screenWidth by remember { mutableIntStateOf(1080) }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { screenWidth = it.width },
    ) {
        // Background reveal (info icon appears as you drag)
        if (dragOffset > 0) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(dragOffset.dp.coerceAtMost(80.dp))
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Informações",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        // Content that drags
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = dragOffset }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = screenWidth * 0.4f
                            if (dragOffset > threshold) onInfoRequested()
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                    ) { change, dragAmount ->
                        if (dragAmount > 0 || dragOffset > 0) {
                            dragOffset = (dragOffset + dragAmount).coerceIn(0f, 300f)
                            change.consume()
                        }
                    }
                },
        ) {
            content()
        }
    }
}

@Composable
private fun RowCard(
    imageUrl: String?,
    shape: ThumbnailShape,
    title: String,
    subtitle: @Composable () -> Unit,
    durationLabel: String?,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    showMoreButton: Boolean,
    mosaic: List<String> = emptyList(),
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mosaic.size >= 4) {
            PlaylistMosaic(mosaic)
        } else {
            MediaThumbnail(
                imageUrl = imageUrl,
                shape = shape,
                size = 56.dp,
                cornerRadius = 8.dp,
                isPlaying = isPlaying,
                isPaused = isPaused,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            subtitle()
        }
        if (durationLabel != null && showMoreButton) {
            Spacer(Modifier.width(8.dp))
            Text(
                durationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        if (showMoreButton) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun PlaylistMosaic(thumbnails: List<String>) {
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f))) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                AsyncImage(model = thumbnails.getOrNull(0), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                AsyncImage(model = thumbnails.getOrNull(2), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
            }
            Column(Modifier.weight(1f)) {
                AsyncImage(model = thumbnails.getOrNull(1), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                AsyncImage(model = thumbnails.getOrNull(3), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

private fun formatDuration(seconds: Long): String? {
    if (seconds <= 0) return null
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}