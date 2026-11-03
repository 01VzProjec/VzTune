package com.vztechcore.vztune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
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
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track

private val GlassFill = Color.Black.copy(alpha = 0.75f)
private val GlassBorder = Color.White.copy(alpha = 0.15f)

@Composable
fun CompactMiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit, // Added next button
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val isVideo = track.source == Source.YOUTUBE

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape) // Clip the whole row to prevent thumbnail overflow
            .background(GlassFill)
            .border(1.dp, GlassBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(if (isVideo) RoundedCornerShape(6.dp) else CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            MediaThumbnail(
                imageUrl = track.thumbnailUrl,
                shape = if (isVideo) ThumbnailShape.SQUARE else ThumbnailShape.CIRCLE,
                size = 40.dp,
                cornerRadius = 6.dp
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(4.dp))

        IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Tocar",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        IconButton(onClick = onNextClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Próxima",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}