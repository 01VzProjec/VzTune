package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.MediaThumbnail
import com.vztechcore.vztune.ui.components.ThumbnailShape
import com.vztechcore.vztune.ui.components.TrackSubtitle

@Composable
internal fun MediaListTrackRow(
    track: Track,
    index: Int?,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    showDragHandle: Boolean,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                "$index",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.width(24.dp),
            )
        }
        if (track.thumbnailUrl == null && index != null) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$index",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            MediaThumbnail(
                imageUrl = track.thumbnailUrl,
                shape = ThumbnailShape.SQUARE,
                size = 52.dp,
                cornerRadius = 6.dp,
                isPlaying = isPlaying,
                isPaused = isPaused,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            TrackSubtitle(
                track = track,
                style = MaterialTheme.typography.bodySmall,
                prefixColor = Color.White.copy(alpha = 0.55f),
                artistColor = Color.White.copy(alpha = 0.6f),
            )
        }

        IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Mais opções",
                tint = Color.White.copy(alpha = 0.7f),
            )
        }

        if (showDragHandle) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Arrastar",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(32.dp)
                    .padding(4.dp)
                    .then(dragHandleModifier),
            )
        }
    }
}

@Composable
internal fun MediaListVideoCard(
    track: Track,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            MediaThumbnail(
                imageUrl = track.thumbnailUrl,
                shape = ThumbnailShape.WIDE,
                cornerRadius = 10.dp,
                isPlaying = isPlaying,
                isPaused = isPaused,
            )
            
            // 3 pontinhos no canto superior direito
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                TrackSubtitle(
                    track = track,
                    style = MaterialTheme.typography.bodySmall,
                    prefixColor = Color.White.copy(alpha = 0.55f),
                    artistColor = Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
internal fun MediaListTabs(selected: MediaListTab, onSelect: (MediaListTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaListTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val bg = if (isSelected) Color.White else Color.White.copy(alpha = 0.08f)
            val fg = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f)
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}