package com.vztechcore.vztune.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.components.feedback.VzLoading

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
                        onMoreClick = { onTrackInfoClick(hit.track) },
                    )
                } else {
                    SearchResultCard(
                        hit = hit,
                        onClick = { onHitClick(hit) },
                        onMoreClick = {
                            val track = when (hit) {
                                is SearchHit.SongHit -> hit.track
                                is SearchHit.VideoHit -> hit.track
                                else -> null
                            }
                            if (track != null) onTrackInfoClick(track)
                        },
                        onPlayNextClick = onPlayNextClick,
                    )
                }
            }

            if (isLoadingMore) {
                item(key = "loading_more") {
                    // VzTechCore logo animation while the next page loads
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
                RowCard(
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
                RowCard(
                    imageUrl = hit.track.thumbnailUrl, shape = ThumbnailShape.SQUARE, title = hit.track.title,
                    subtitle = { TrackSubtitle(hit.track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f)) },
                    durationLabel = null,
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
            RowCard(
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
        is SearchHit.ArtistHit -> RowCard(
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
            RowCard(
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
    val nowPlaying = LocalNowPlaying.current
    val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }) {
        // 16:9 thumbnail only — the "more" button now lives next to the title/channel
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            MediaThumbnail(
                imageUrl = hit.track.thumbnailUrl, shape = ThumbnailShape.WIDE, cornerRadius = 12.dp,
                isPlaying = isCurrent && nowPlaying.isPlaying, isPaused = isCurrent && !nowPlaying.isPlaying,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(hit.track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                TrackSubtitle(hit.track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f))
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun SwipeableRow(
    onPlayNext: () -> Unit,
    content: @Composable () -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (dragOffset > 0f) {
            val iconAlpha = (dragOffset / 100f).coerceIn(0.4f, 1f)
            Box(
                Modifier.fillMaxHeight().width(dragOffset.dp.coerceAtMost(80.dp)).padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Tocar em seguida",
                    tint = Color.White.copy(alpha = iconAlpha),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().graphicsLayer { translationX = dragOffset }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffset > 30f) onPlayNext()
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
        ) { content() }
    }
}

@Composable
private fun RowCard(
    imageUrl: String?, shape: ThumbnailShape, title: String, subtitle: @Composable () -> Unit,
    durationLabel: String?, isPlaying: Boolean, isPaused: Boolean, onClick: () -> Unit,
    onMoreClick: () -> Unit, showMoreButton: Boolean, mosaic: List<String> = emptyList(),
) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (mosaic.size >= 4) {
            // PlaylistMosaic (shared component) doesn't fix its own size — the caller defines it.
            Box(Modifier.size(56.dp)) { PlaylistMosaic(mosaic) }
        } else {
            MediaThumbnail(imageUrl = imageUrl, shape = shape, size = 56.dp, cornerRadius = 8.dp, isPlaying = isPlaying, isPaused = isPaused)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            subtitle()
        }
        if (durationLabel != null && showMoreButton) {
            Spacer(Modifier.width(8.dp))
            Text(durationLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        }
        if (showMoreButton) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
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