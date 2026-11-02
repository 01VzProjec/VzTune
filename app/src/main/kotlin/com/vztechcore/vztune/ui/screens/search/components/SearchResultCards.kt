package com.vztechcore.vztune.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
            contentPadding = PaddingValues(
                bottom = 160.dp,
                top = 8.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (isVideoMode) 20.dp else 4.dp),
        ) {
            itemsIndexed(hits, key = { _, item -> item.stableKey }) { _, hit ->
                if (isVideoMode && hit is SearchHit.VideoHit) {
                    VideoResultCard(
                        hit = hit,
                        onClick = { onHitClick(hit) },
                        onMoreClick = { /* TODO: menu de 3 pontinhos */ }
                    )
                } else {
                    SearchResultCard(
                        hit = hit,
                        onClick = { onHitClick(hit) },
                        onMoreClick = { /* TODO: menu de 3 pontinhos */ }
                    )
                }
            }

            if (isLoadingMore) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                        )
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
) {
    val nowPlaying = LocalNowPlaying.current

    when (hit) {
        is SearchHit.SongHit -> {
            val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
            val isPlaying = isCurrent && nowPlaying.isPlaying
            val isPaused = isCurrent && !nowPlaying.isPlaying
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
                isPlaying = isPlaying,
                isPaused = isPaused,
                onClick = onClick,
                onMoreClick = onMoreClick,
            )
        }

        is SearchHit.VideoHit -> {
            val isCurrent = nowPlaying.isCurrent(hit.track.id, hit.track.source)
            val isPlaying = isCurrent && nowPlaying.isPlaying
            val isPaused = isCurrent && !nowPlaying.isPlaying
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
                isPlaying = isPlaying,
                isPaused = isPaused,
                onClick = onClick,
                onMoreClick = onMoreClick,
            )
        }

        is SearchHit.PlaylistHit -> RowCard(
            imageUrl = hit.playlist.thumbnailUrl,
            mosaic = hit.playlist.mosaicThumbnails,
            shape = ThumbnailShape.SQUARE,
            title = hit.playlist.title,
            subtitle = {
                Text(
                    text = "Playlist · ${hit.playlist.owner ?: ""}",
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
        )

        is SearchHit.ArtistHit -> RowCard(
            imageUrl = hit.artist.thumbnailUrl,
            shape = ThumbnailShape.CIRCLE,
            title = hit.artist.name,
            subtitle = {
                Text(
                    text = "Artista",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            },
            durationLabel = null,
            isPlaying = false,
            isPaused = false,
            onClick = onClick,
            onMoreClick = onMoreClick,
        )

        is SearchHit.AlbumHit -> RowCard(
            imageUrl = hit.album.thumbnailUrl,
            shape = ThumbnailShape.SQUARE,
            title = hit.album.title,
            subtitle = {
                Text(
                    text = "Álbum · ${hit.album.artist}",
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
    val isPlaying = isCurrent && nowPlaying.isPlaying
    val isPaused = isCurrent && !nowPlaying.isPlaying

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
                isPlaying = isPlaying,
                isPaused = isPaused,
            )

            // 3 pontinhos no canto superior direito da thumb 16:9
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Mais opções",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = hit.track.title,
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
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            subtitle()
        }
        
        if (durationLabel != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        
        // 3 pontinhos na lista comum (songs/playlists/albums)
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Mais opções",
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun PlaylistMosaic(thumbnails: List<String>) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f)),
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                AsyncImage(
                    model = thumbnails.getOrNull(0),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AsyncImage(
                    model = thumbnails.getOrNull(2),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            Column(Modifier.weight(1f)) {
                AsyncImage(
                    model = thumbnails.getOrNull(1),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                AsyncImage(
                    model = thumbnails.getOrNull(3),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
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