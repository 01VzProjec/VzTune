package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
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
import com.vztechcore.vztune.ui.components.LocalNowPlaying
import com.vztechcore.vztune.ui.components.MediaThumbnail
import com.vztechcore.vztune.ui.components.ThumbnailShape
import com.vztechcore.vztune.ui.components.TrackSubtitle
import com.vztechcore.vztune.ui.components.VzChipRow
import com.vztechcore.vztune.ui.components.playbackOf

@Composable
internal fun MediaListTrackRow(
    track: Track, index: Int?, isPlaying: Boolean, isPaused: Boolean,
    onClick: () -> Unit, onMoreClick: () -> Unit, onPlayNext: () -> Unit,
    showDragHandle: Boolean, dragHandleModifier: Modifier, modifier: Modifier = Modifier,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (dragOffset > 0f) {
            val iconAlpha = (dragOffset / 100f).coerceIn(0.4f, 1f)
            Box(Modifier.fillMaxHeight().width(dragOffset.dp.coerceAtMost(80.dp)).padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Tocar em seguida", tint = Color.White.copy(alpha = iconAlpha), modifier = Modifier.size(24.dp))
            }
        }
        Row(
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
                }
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (index != null) Text("$index", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.width(24.dp))
            if (track.thumbnailUrl == null && index != null) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Text("$index", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                MediaThumbnail(imageUrl = track.thumbnailUrl, shape = ThumbnailShape.SQUARE, size = 52.dp, cornerRadius = 6.dp, isPlaying = isPlaying, isPaused = isPaused)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                TrackSubtitle(track = track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f))
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
            if (showDragHandle) {
                Icon(Icons.Filled.DragHandle, contentDescription = "Arrastar", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp).padding(4.dp).then(dragHandleModifier))
            }
        }
    }
}

@Composable
internal fun MediaListVideoCard(
    track: Track, isPlaying: Boolean, isPaused: Boolean, onClick: () -> Unit, onMoreClick: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp)) {
        // 16:9 thumbnail only — the "more" button now lives next to the title/channel
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            MediaThumbnail(imageUrl = track.thumbnailUrl, shape = ThumbnailShape.WIDE, cornerRadius = 10.dp, isPlaying = isPlaying, isPaused = isPaused)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                TrackSubtitle(track = track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f))
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
internal fun MediaListTabs(selected: MediaListTab, onSelect: (MediaListTab) -> Unit) {
    VzChipRow(
        items = MediaListTab.entries,
        selected = selected,
        label = { it.label },
        onSelect = onSelect,
    )
}

/** Standard track row that reads the now-playing state itself (equalizer on the thumbnail). */
@Composable
internal fun PlayableTrackRow(
    track: Track,
    index: Int?,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    MediaListTrackRow(
        track = track,
        index = index,
        isPlaying = playback.isPlaying,
        isPaused = playback.isPaused,
        onClick = onClick,
        onMoreClick = onMoreClick,
        onPlayNext = onPlayNext,
        showDragHandle = false,
        dragHandleModifier = Modifier,
        modifier = modifier,
    )
}

/** Standard 16:9 video card that reads the now-playing state itself. */
@Composable
internal fun PlayableVideoCard(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    MediaListVideoCard(
        track = track,
        isPlaying = playback.isPlaying,
        isPaused = playback.isPaused,
        onClick = onClick,
        onMoreClick = onMoreClick,
        modifier = modifier,
    )
}
