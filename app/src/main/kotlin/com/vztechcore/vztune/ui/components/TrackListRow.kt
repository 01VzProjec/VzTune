package com.vztechcore.vztune.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track

/**
 * Standard track card: optional ranking number, thumbnail with the "now playing" equalizer,
 * title, artist (with the explicit badge) and the 3-dots button on the right.
 * Used by the artist screens and the Library.
 *
 * Sized to match the compact Search result rows (thumbnail ~52.dp) so the whole app
 * shows the same visual weight for track lists.
 */
@Composable
fun TrackListRow(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)

    Row(
        modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(26.dp),
            )
        }
        MediaThumbnail(
            imageUrl = track.thumbnailUrl,
            size = 52.dp,
            cornerRadius = 6.dp,
            isPlaying = playback.isPlaying,
            isPaused = playback.isPaused,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    ExplicitBadge()
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Mais",
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}