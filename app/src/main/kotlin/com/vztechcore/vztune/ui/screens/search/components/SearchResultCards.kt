package com.vztechcore.vztune.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.ui.components.MediaThumbnail
import com.vztechcore.vztune.ui.components.ThumbnailShape
import com.vztechcore.vztune.ui.components.TrackSubtitle
import com.vztechcore.vztune.ui.components.LocalNowPlaying

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
                    VideoResultCard(hit = hit, onClick = { onHitClick(hit) })
                } else {
                    SearchResultCard(hit = hit, onClick = { onHitClick(hit) })
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
) {
    val nowPlaying = LocalNowPlaying.current
    when (hit) {
        is SearchHit.SongHit -> {
            val isPlaying = nowPlaying.isCurrent(hit.track.id, hit.track.source)
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
                onClick = onClick,
            )
        }
        is SearchHit.VideoHit -> {
            val isPlaying = nowPlaying.isCurrent(hit.track.id, hit.track.source)
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
                onClick = onClick,
            )
        }
        is SearchHit.PlaylistHit -> RowCard(
            imageUrl = hit.playlist.thumbnailUrl,
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
            onClick = onClick,
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
            onClick = onClick,
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
            onClick = onClick,
        )
    }
}

@Composable
fun VideoResultCard(hit: SearchHit.VideoHit, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        MediaThumbnail(
            imageUrl = hit.track.thumbnailUrl,
            shape = ThumbnailShape.WIDE,
            cornerRadius = 12.dp,
        )
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
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumbnail(
            imageUrl = imageUrl,
            shape = shape,
            size = 56.dp,
            cornerRadius = 8.dp,
            isPlaying = isPlaying,   // ← equalizer aparece aqui, não no mini player
        )
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
    }
}

private fun formatDuration(seconds: Long): String? {
    if (seconds <= 0) return null
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}